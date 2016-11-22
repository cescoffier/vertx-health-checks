package me.escoffier.vertx.healthchecks;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.jayway.awaitility.Awaitility.await;
import static me.escoffier.vertx.healthchecks.Assertions.assertThatCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.hamcrest.Matchers.is;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthCheckTest {

  private Vertx vertx;
  private HealthCheckHandler handler;

  //TODO Error in checks (500)

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
  public void testEmptyChecks() {
    RestAssured.get("/health")
      .then()
      .statusCode(204);
  }

  @Test
  public void testWithEmptySuccessfulCheck() {
    handler.register("foo", Future::complete);

    JsonObject json = get(200);
    assertThatCheck(json).hasOutcomeUp()
      .hasChildren(1)
      .hasAndGetCheck("foo").isUp().done();
  }

  @Test
  public void testWithEmptyFailedCheck() {
    handler.register("foo", future -> future.fail("BOOM"));

    JsonObject json = get(503);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "BOOM").done();
  }

  @Test
  public void testWithExplicitSuccessfulCheck() {
    handler.register("bar", future -> future.complete(Status.OK()));

    JsonObject json = get(200);
    assertThatCheck(json).hasOutcomeUp()
      .hasChildren(1)
      .hasAndGetCheck("bar").isUp().done();
  }

  @Test
  public void testWithExplicitFailedCheck() {
    handler.register("bar", future -> future.complete(Status.KO()));

    JsonObject json = get(503);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("bar").isDown().done();
  }

  @Test
  public void testWithExplicitSuccessfulCheckAndData() {
    handler.register("bar", future -> future.complete(Status.OK(new JsonObject()
      .put("availableMemory", "2Mb"))));

    JsonObject json = get(200);
    assertThat(json.getMap())
      .contains(entry("outcome", "UP"));

    JsonArray array = json.getJsonArray("checks");
    assertThat(array.getList()).hasSize(1);
    JsonObject check = array.getJsonObject(0);
    assertThat(check.getMap()).hasSize(3)
      .contains(entry("status", "UP"), entry("id", "bar"),
        entry("data", ImmutableMap.of("availableMemory", "2Mb")));
  }

  @Test
  public void testWithExplicitFailedCheckAndData() {
    handler.register("bar", future -> future.complete(Status.KO(new JsonObject()
      .put("availableMemory", "2Mb"))));

    JsonObject json = get(503);
    assertThat(json.getMap())
      .contains(entry("outcome", "DOWN"));

    JsonArray array = json.getJsonArray("checks");
    assertThat(array.getList()).hasSize(1);
    JsonObject check = array.getJsonObject(0);
    assertThat(check.getMap()).hasSize(3)
      .contains(entry("status", "DOWN"), entry("id", "bar"),
        entry("data", ImmutableMap.of("availableMemory", "2Mb")));
  }

  @Test
  public void testWithOneSuccessfulAndOneFailedCheck() {
    handler
      .register("s", future -> future.complete(Status.OK()))
      .register("f", future -> future.complete(Status.KO()));

    JsonObject json = get(503);
    assertThatCheck(json).hasOutcomeDown().hasChildren(2);
  }

  @Test
  public void testWithTwoFailedChecks() {
    handler
      .register("f1", future -> future.complete(Status.KO()))
      .register("f2", future -> future.complete(Status.KO()));

    JsonObject json = get(503);
    assertThatCheck(json).hasOutcomeDown().hasChildren(2);
  }

  @Test
  public void testWithTwoSucceededChecks() {
    handler
      .register("s1", future -> future.complete(Status.OK()))
      .register("s2", future -> future.complete(Status.OK()));

    JsonObject json = get(200);
    assertThatCheck(json).hasOutcomeUp().hasChildren(2);
  }

  @Test
  public void testWithNestedCompositeThatSucceed() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.OK()));

    JsonObject json = get(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp()
      .hasChildren(2)
      .hasAndGetCheck("sub").hasStatusUp()
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done()

      .hasAndGetCheck("sub2").hasStatusUp()
      .hasAndGetCheck("c").hasStatusUp()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusUp().done()
      .done()

      .done();
  }

  @Test
  public void testWithNestedCompositeThatFailed() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = get(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown()
      .hasChildren(2)
      .hasAndGetCheck("sub").hasStatusUp()
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done()

      .hasAndGetCheck("sub2").hasStatusDown()
      .hasAndGetCheck("c").hasStatusDown()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done()
      .done()

      .done();
  }


  @Test
  public void testRetrievingAComposite() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = get("sub", 200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp()
      .hasChildren(2)
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done();

    json = get("sub2", 503);

    assertThatCheck(json)
      .hasAndGetCheck("c").hasStatusDown()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done()
      .done();

    json = get("sub2/c", 503);

    assertThatCheck(json)
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done();
  }

  @Test
  public void testRetrievingALeaf() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = get("sub/A", 200);

    assertThatCheck(json)
      .isUp()
      .hasStatusUp()
      .hasOutcomeUp()
      .done();

    json = get("sub2/c/C2", 503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown()
      .hasStatusDown()
      .done();

    // Not found
    get("missing", 404);
    // Illegal
    get("sub2/c/C1/foo", 400);
  }

  @Test
  public void testACheckThatTimeOut() {
    handler.register("foo", future -> {
      // Bad boy !
    });

    JsonObject json = get(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown()
      .hasData("procedure-execution-failure", true)
      .hasData("cause", "Timeout").done();
  }

  @Test
  public void testACheckThatFail() {
    handler.register("foo", future -> {
      throw new IllegalArgumentException("BOOM");
    });

    JsonObject json = get(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown()
      .hasData("cause", "BOOM")
      .hasData("procedure-execution-failure", true)
      .done();
  }


  @Test
  public void testACheckThatTimeOutButSucceed() {
    handler.register("foo", future -> vertx.setTimer(2000, l -> future.complete()));

    JsonObject json = get(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "Timeout").done();
  }

  @Test
  public void testACheckThatTimeOutButFailed() {
    handler.register("foo", future -> vertx.setTimer(2000, l -> future.fail("BOOM")));

    JsonObject json = get(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "Timeout").done();
  }

  @Test
  public void testRemovingComposite() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = get(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown();

    handler.unregister("sub2/c");

    json = get(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp();
  }

  @Test
  public void testRemovingLeaf() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = get(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown();

    handler.unregister("sub2/c/C2");

    json = get(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidRegistrationOfAProcedure() {
    handler.register("foo", future -> future.complete(Status.OK()));
    handler.register("foo/bar", future -> future.complete(Status.OK()));
  }

  @Test(expected = NullPointerException.class)
  public void testRegistrationWithNoName() {
    handler.register(null, future -> future.complete(Status.OK()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRegistrationWithEmptyName() {
    handler.register("", future -> future.complete(Status.OK()));
  }

  @Test(expected = NullPointerException.class)
  public void testRegistrationWithNoProcedure() {
    handler.register("bad", null);
  }


  private JsonObject get(int status) {
    String json = RestAssured.get("/health")
      .then()
      .statusCode(status)
      .header("content-type", "application/json;charset=UTF-8")
      .extract().asString();
    return new JsonObject(json);
  }

  private JsonObject get(String path, int status) {
    String json = RestAssured.get("/health/" + path)
      .then()
      .statusCode(status)
      .header("content-type", "application/json;charset=UTF-8")
      .extract().asString();
    return new JsonObject(json);
  }

}
