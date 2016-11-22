package me.escoffier.vertx.healthchecks;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Assertions {

  public static CheckAssert assertThatCheck(JsonObject json) {
    return new CheckAssert(json);
  }

}
