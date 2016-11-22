package me.escoffier.vertx.healthchecks;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Represents the outcome of a health check procedure. Each procedure produces a {@link Status} indicating either UP
 * or DOWN. Optionally, it can also provide additional data.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@DataObject(generateConverter = true)
public class Status {

  /**
   * Creates a status when everything is fine.
   *
   * @return the created {@link Status}
   */
  public static Status OK() {
    return new Status().setUp(true);
  }

  /**
   * Creates a status when everything is fine and adds metadata.
   *
   * @return the created {@link Status}
   */
  public static Status OK(JsonObject data) {
    return new Status().setUp(true).setData(data);
  }

  /**
   * Creates a status when something bad is detected.
   *
   * @return the created {@link Status}
   */
  public static Status KO() {
    return new Status().setUp(false);
  }


  /**
   * Creates a status when something bad is detected. Also add some metadata.
   *
   * @return the created {@link Status}
   */
  public static Status KO(JsonObject data) {
    return new Status().setUp(false).setData(data);
  }

  /**
   * Whether or not the check is positive or negative.
   */
  private boolean up;

  /**
   * Optional metadata attached to the status.
   */
  private JsonObject data = new JsonObject();

  /**
   * Flag denoting a failure, such as a timeout or a procedure throwing an exception.
   */
  private boolean procedureInError;

  public Status() {
    // Empty constructor
  }

  public Status(Status other) {
    this.up = other.up;
    this.data = other.data;
    this.procedureInError = other.procedureInError;
  }

  public Status(JsonObject json) {
    StatusConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    StatusConverter.toJson(this, json);
    return json;
  }

  /**
   * @return whether or not the current status is positive or negative.
   */
  public boolean isUp() {
    return up;
  }

  /**
   * Sets whether or not the current status is positive (UP) or negative (DOWN).
   *
   * @param up {@code true} for UP, {@code false} for DOWN
   * @return the current status
   */
  public Status setUp(boolean up) {
    this.up = up;
    return this;
  }

  /**
   * @return the additional metadata.
   */
  public JsonObject getData() {
    return data;
  }

  /**
   * Sets the metadata.
   *
   * @param data the data
   * @return the current status
   */
  public Status setData(JsonObject data) {
    this.data = data;
    return this;
  }

  /**
   * @return whether or not the status denotes a failure of a procedure.
   */
  public boolean isProcedureInError() {
    return procedureInError;
  }

  /**
   * Sets whether or not the procedure attached to this status has failed (timeout, error...).
   *
   * @param procedureInError {@code true} if the procedure has not been completed correctly.
   * @return the current status
   */
  public Status setProcedureInError(boolean procedureInError) {
    this.procedureInError = procedureInError;
    return this;
  }
}
