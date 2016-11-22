package me.escoffier.vertx.healthchecks;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import me.escoffier.vertx.healthchecks.impl.HealthCheckHandlerImpl;

import java.util.Objects;

/**
 * A Vert.x Web handler on which you register health check procedure. It computes the outcome status (`UP` or `DOWN`)
 * . When the handler process a HTTP request, it computes the global outcome and build a HTTP response as follows:
 * <p>
 * <ul>
 * <li>204 - status is `UP` but no procedures installed (no payload)</li>
 * <li>200 - status is `UP`, the payload contains the result of the installed procedures</li>
 * <li>503 - status is `DOWN`, the payload contains the result of the installed procedures</li>
 * <li>500 - status is `DOWN`, the payload contains the result of the installed procedures, one of the
 * procedure has failed</li>
 * </ul>
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface HealthCheckHandler extends Handler<RoutingContext> {

  /**
   * Creates an instance of the default implementation of the {@link HealthCheckHandler}.
   *
   * @param vertx the Vert.x instance, must not be {@code null}
   * @return the created instance
   */
  static HealthCheckHandler create(Vertx vertx) {
    Objects.requireNonNull(vertx);
    return new HealthCheckHandlerImpl(vertx);
  }

  /**
   * Registers a health check procedure.
   * <p>
   * The procedure is a {@link Handler} taking a {@link Future} of {@link Status} as parameter. Procedures are
   * asynchronous, and <strong>must</strong> complete or fail the given {@link Future}. If the future object is
   * failed, the procedure outcome is considered as `DOWN`. If the future is completed without any object, the
   * procedure outcome is considered as `UP`. If the future is completed with a (not-null) {@link Status}, the
   * procedure outcome is the received status.
   *
   * @param name      the name of the procedure, must not be {@code null} or empty
   * @param procedure the procedure, must not be {@code null}
   * @return the current {@link HealthCheckHandler}
   */
  HealthCheckHandler register(String name, Handler<Future<Status>> procedure);

  /**
   * Unregisters a procedure.
   *
   * @param name the name of the procedure
   * @return the current {@link HealthCheckHandler}
   */
  HealthCheckHandler unregister(String name);


}
