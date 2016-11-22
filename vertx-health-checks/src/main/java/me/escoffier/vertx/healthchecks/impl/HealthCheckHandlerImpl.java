package me.escoffier.vertx.healthchecks.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import me.escoffier.vertx.healthchecks.HealthCheckHandler;
import me.escoffier.vertx.healthchecks.Status;

import java.util.Objects;

import static me.escoffier.vertx.healthchecks.impl.StatusHelper.isUp;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthCheckHandlerImpl implements HealthCheckHandler {

  private final Vertx vertx;
  private CompositeProcedure root = new DefaultCompositeProcedure();

  public HealthCheckHandlerImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public HealthCheckHandler register(String name, Handler<Future<Status>> procedure) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("The name must not be empty");
    }
    Objects.requireNonNull(procedure);
    String[] segments = name.split("/");
    CompositeProcedure parent = traverseAndCreate(segments);
    String lastSegment = segments[segments.length - 1];
    parent.add(lastSegment,
      new DefaultProcedure(vertx, lastSegment, 1000, procedure));
    return this;
  }

  private CompositeProcedure traverseAndCreate(String[] segments) {
    int i;
    CompositeProcedure parent = root;
    for (i = 0; i < segments.length - 1; i++) {
      Procedure c = parent.get(segments[i]);
      if (c == null) {
        DefaultCompositeProcedure composite = new DefaultCompositeProcedure();
        parent.add(segments[i], composite);
        parent = composite;
      } else if (c instanceof CompositeProcedure) {
        parent = (CompositeProcedure) c;
      } else {
        // Illegal.
        throw new IllegalArgumentException("Unable to find the procedure `" + segments[i] + "`, `"
          + segments[i] + "` is not a composite.");
      }
    }

    return parent;
  }

  private CompositeProcedure findLastParent(String[] segments) {
    int i;
    CompositeProcedure parent = root;
    for (i = 0; i < segments.length - 1; i++) {
      Procedure c = parent.get(segments[i]);
      if (c instanceof CompositeProcedure) {
        parent = (CompositeProcedure) c;
      } else {
        return null;
      }
    }
    return parent;
  }

  @Override
  public synchronized HealthCheckHandler unregister(String name) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("The name must not be empty");
    }
    String[] segments = name.split("/");
    CompositeProcedure parent = findLastParent(segments);
    if (parent != null) {
      String lastSegment = segments[segments.length - 1];
      parent.remove(lastSegment);
    }
    return this;
  }

  @Override
  public void handle(RoutingContext rc) {
    Procedure check = root;

    String id = rc.request().path().substring(rc.currentRoute().getPath().length());
    if (!id.isEmpty()) {
      String[] segments = id.split("/");
      for (String segment : segments) {
        if (segment.trim().isEmpty()) {
          continue;
        }
        if (check instanceof CompositeProcedure) {
          check = ((CompositeProcedure) check).get(segment);
          if (check == null) {
            rc.response().setStatusCode(404)
              .putHeader("content-type", "application/json;charset=UTF-8")
              .end("{\"error\":\"Procedure not found '" + segment + "'\"}");
            return;
          }
        } else {
          // Not a composite
          rc.response().setStatusCode(400)
            .putHeader("content-type", "application/json;charset=UTF-8")
            .end("{\"error\":\"The procedure '" + segment + "' cannot be a child of a " +
              "leaf\"}");
          return;
        }
      }
    }

    check.check(json -> {
      int status = isUp(json) ? 200 : 503;

      if (status == 503 && hasProcedureError(json)) {
        status = 500;
      }

      JsonArray checks = json.getJsonArray("checks");
      if (status == 200 && checks != null && checks.isEmpty()) {
        // Special case, no procedure installed.
        rc.response().setStatusCode(204).end();
        return;
      }

      rc.response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .setStatusCode(status)
        .end(transform(json));
    });
  }

  private boolean hasProcedureError(JsonObject json) {
    JsonObject data = json.getJsonObject("data");
    if (data != null && data.getBoolean("procedure-execution-failure", false)) {
      return true;
    }

    JsonArray checks = json.getJsonArray("checks");
    if (checks != null) {
      for (int i = 0; i < checks.size(); i++) {
        JsonObject check = checks.getJsonObject(i);
        if (hasProcedureError(check)) {
          return true;
        }
      }
    }

    return false;
  }

  private String transform(JsonObject json) {
    String status = json.getString("status");
    String outcome = json.getString("outcome");
    if (status != null && outcome == null) {
      json.put("outcome", status);
    }
    return json.encode();
  }
}
