package me.escoffier.vertx.healthchecks;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.jayway.awaitility.Awaitility.await;
import static me.escoffier.vertx.healthchecks.HealthCheckTest.get;
import static org.hamcrest.Matchers.is;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class CommonHealthChecksTest {


  private Vertx vertx;
  private HealthCheckHandler handler;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    handler = HealthCheckHandler.create(vertx);
    router.get("/health*").handler(handler);

    AtomicBoolean done = new AtomicBoolean();
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080, ar -> done.set(ar.succeeded()));
    await().untilAtomic(done, is(true));

    RestAssured.baseURI = "http://localhost";
    RestAssured.port = 8080;
  }

  @After
  public void tearDown() {
    AtomicBoolean done = new AtomicBoolean();
    vertx.close(v -> done.set(v.succeeded()));
    await().untilAtomic(done, is(true));
  }

  @Test
  public void testJDBC_OK() {
    JsonObject config = new JsonObject()
      .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
      .put("driver_class", "org.hsqldb.jdbcDriver");
    JDBCClient client = JDBCClient.createShared(vertx, config);

    handler.register("database",
      future -> client.getConnection(connection -> {
        if (connection.failed()) {
          future.fail(connection.cause());
        } else {
          connection.result().close();
          future.complete(Status.OK());
        }
      }));
    get(200);
  }


  @Test
  public void testJDBC_KO() {
    JsonObject config = new JsonObject()
      .put("url", "jdbc:missing:mem:test?shutdown=true")
      .put("driver_class", "org.hsqldb.jdbcDriver");
    JDBCClient client = JDBCClient.createShared(vertx, config);

    handler.register("database",
      future -> client.getConnection(connection -> {
        if (connection.failed()) {
          future.fail(connection.cause());
        } else {
          connection.result().close();
          future.complete(Status.OK());
        }
      }));
    // We use 'fail'
    get(500);
  }


  @Test
  public void testServiceAvailability_OK() {
    ServiceDiscovery discovery = ServiceDiscovery.create(vertx);
    AtomicBoolean done = new AtomicBoolean();
    discovery.publish(HttpEndpoint.createRecord("my-service", "localhost"), ar -> {
      done.set(ar.succeeded());
    });
    await().untilAtomic(done, is(true));

    handler.register("service",
      future -> HttpEndpoint.getClient(discovery,
        (rec) -> "my-service".equals(rec.getName()),
        client -> {
          if (client.failed()) {
            future.fail(client.cause());
          } else {
            client.result().close();
            future.complete(Status.OK());
          }
        }));
    get(200);
  }

  @Test
  public void testServiceAvailability_KO() {
    ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

    handler.register("service",
      future -> HttpEndpoint.getClient(discovery,
        (rec) -> "my-service".equals(rec.getName()),
        client -> {
          if (client.failed()) {
            future.fail(client.cause());
          } else {
            client.result().close();
            future.complete(Status.OK());
          }
        }));
    get(503);
  }

  @Test
  public void testOnEventBus_OK() {
    vertx.eventBus().consumer("health", ar -> {
      ar.reply("pong");
    });

    handler.register("receiver",
      future ->
        vertx.eventBus().send("health", "ping", response -> {
          if (response.succeeded()) {
            future.complete(Status.OK());
          } else {
            future.complete(Status.KO());
          }
        })
    );

    get(200);

  }

  @Test
  public void testOnEventBus_KO() {
    vertx.eventBus().consumer("health", ar -> {
      ar.fail(500, "BOOM !");
    });

    handler.register("receiver",
      future ->
        vertx.eventBus().send("health", "ping", response -> {
          if (response.succeeded()) {
            future.complete(Status.OK());
          } else {
            future.complete(Status.KO());
          }
        })
    );

    get(503);

  }

  @Test
  public void testOnEventBus_KO_no_receiver() {
    handler.register("receiver",
      future ->
        vertx.eventBus().send("health", "ping", response -> {
          if (response.succeeded()) {
            future.complete(Status.OK());
          } else {
            future.complete(Status.KO());
          }
        })
    );

    get(503);

  }


}
