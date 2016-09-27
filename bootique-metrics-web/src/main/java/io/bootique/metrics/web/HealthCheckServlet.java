package io.bootique.metrics.web;

import io.bootique.metrics.health.HealthCheckOutcome;
import io.bootique.metrics.health.HealthCheckRegistry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * A servlet that executes app healthchecks and returns text status document. By default mapped as "/health" in the
 * web application context.
 *
 * @since 0.8
 */
// inspired com.yammer.metrics.servlet.HealthCheckServlet, only better integrated to Bootique and using our own format
// TODO: config-driven verbosity levels .. perhaps use nagios plugin format?
public class HealthCheckServlet extends HttpServlet {

    private static final String CONTENT_TYPE = "text/plain";
    private HealthCheckRegistry registry;

    public HealthCheckServlet(HealthCheckRegistry registry) {
        this.registry = registry;
    }

    private static boolean isAllHealthy(Map<String, HealthCheckOutcome> results) {
        for (HealthCheckOutcome result : results.values()) {
            if (!result.isHealthy()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try (PrintWriter writer = response.getWriter()) {
            doWrite(response, writer);
        }
    }

    protected void doWrite(HttpServletResponse response, PrintWriter writer) throws IOException {

        Map<String, HealthCheckOutcome> results = registry.runHealthChecks();

        response.setContentType(CONTENT_TYPE);
        response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");

        if (results.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
            writer.println("! No health checks registered.");
            return;
        }

        if (isAllHealthy(results)) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        for (Map.Entry<String, HealthCheckOutcome> entry : results.entrySet()) {

            HealthCheckOutcome result = entry.getValue();
            if (result.isHealthy()) {
                if (result.getMessage() != null) {
                    writer.format("* %s: OK - %s\n", entry.getKey(), result.getMessage());
                } else {
                    writer.format("* %s: OK\n", entry.getKey());
                }
            } else {
                if (result.getMessage() != null) {
                    writer.format("! %s: ERROR - %s\n", entry.getKey(), result.getMessage());
                }

                @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                Throwable error = result.getError();
                if (error != null) {
                    writer.println();
                    error.printStackTrace(writer);
                    writer.println();
                }
            }
        }
    }
}
