/**
 * = Vert.x Health Checks
 *
 * This component provides a simple way to expose health checks. Health checks are used to express the current state
 * of the application in very simple terms: _UP_ or _DOWN_. This component provides a Vert.x Web handler on which you
 * can register procedure testing the health of the application. The handler computes the final state and returns the
 * result as JSON.
 *
 * == Using Vert.x Health Checks
 *
 * Notice that you need Vert.x Web to use this component. In addition add the following dependency:
 *
 * * Maven (in your `pom.xml`):
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>${maven.groupId}</groupId>
 *   <artifactId>${maven.artifactId}</artifactId>
 *   <version>${maven.version}</version>
 * </dependency>
 * ----
 *
 * * Gradle (in your `build.gradle` file):
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * compile '${maven.groupId}:${maven.artifactId}:${maven.version}'
 * ----
 *
 * === Registering the handler
 *
 * First you need to create the health check and then register procedures checking the health:
 *
 * [source]
 * ----
 * {@link examples.Examples#example2(io.vertx.core.Vertx)}
 * ----
 *
 * Procedure registration is directly made on the {@link me.escoffier.vertx.healthchecks.HealthCheckHandler} instance
 * . It can be done at anytime, even after the route registration or at runtime:
 *
 * [source]
 * ----
 * {@link examples.Examples#example2(io.vertx.core.Vertx, io.vertx.ext.web.Router)}
 * ----
 *
 * A procedure has a name, and a function (handler) executing the check. This function must not block and report to
 * the given {@link io.vertx.core.Future} whether or not it succeed. Rules are the following:
 *
 *
 * * if the future is mark as failed, the check is considered as _KO_
 * * if the future is completed successfully but without a {@link me.escoffier.vertx.healthchecks.Status}, the check
 * is considered as _OK_.
 * * if the future is completed successfully with a {@link me.escoffier.vertx.healthchecks.Status} marked as _OK_,
 * the check is considered as _OK_.
 * * if the future is completed successfully with a {@link me.escoffier.vertx.healthchecks.Status} marked as _KO_,
 * the check is considered as _KO_.
 *
 * {@link me.escoffier.vertx.healthchecks.Status} can also provide additional data:
 *
 * [source]
 * ----
 * {@link examples.Examples#example4(io.vertx.core.Vertx, io.vertx.ext.web.Router)}
 * ----
 *
 * Procedures can be organised by groups. The procedure name indicates the group. The procedures are organized as a
 * tree and the structure is mapped to HTTP urls (see below).
 *
 * [source]
 * ----
 * {@link examples.Examples#example3(io.vertx.core.Vertx, io.vertx.ext.web.Router)}
 * ----
 *
 * == HTTP responses and JSON Output
 *
 * The overall health check is retrieved using a HTTP GET on the route given when exposing the
 * {@link me.escoffier.vertx.healthchecks.HealthCheckHandler}.
 *
 * If no procedure are registered, the response is `204 - NO CONTENT`, indicating that the system is _UP_ but no
 * procedures has been executed. The response does not contain a payload.
 *
 * If there is at least one procedure registered, this procedure is executed and the outcome status is computed. The
 * response would use the following status code:
 *
 * * `200` : Everything is fine
 * * `503` : At least one procedure has reported a non-healthy state
 * * `500` : One procedure has thrown an error or has not reported a status in time
 *
 * The content is a JSON document indicating the overall result (`outcome`). It's either `UP` or `DOWN`. A `checks`
 * array is also given indicating the result of the different executed procedures. If the procedure has reported
 * additional data, the data is also given:
 *
 * [source]
 * ----
 * {
 *  "checks" : [
 *  {
 *    "id" : "A",
 *    "status" : "UP"
 *  },
 *  {
 *    "id" : "B",
 *    "status" : "DOWN",
 *    "data" : {
 *      "some-data" : "some-value"
 *    }
 *  }
 *  ],
 *  "outcome" : "DOWN"
 * }
 * ----
 *
 * In case of groups/ hierarchy, the `checks` array depicts this structure:
 *
 * [source]
 * ----
 * {
 *  "checks" : [
 *  {
 *    "id" : "my-group",
 *    "status" : "UP",
 *    "checks" : [
 *    {
 *      "id" : "check-2",
 *      "status" : "UP",
 *    },
 *    {
 *      "id" : "check-1",
 *      "status" : "UP"
 *    }]
 *  }],
 *  "outcome" : "UP"
 * }
 * ----
 *
 * If a procedure throws an error, reports a failure (exception), the JSON document provides the `cause` in the
 * `data` section. If a procedure does not report back before a timeout, the indicated cause is `Timeout`.
 *
 * == Examples of procedures
 *
 * This section provides example of common health checks.
 *
 * === JDBC
 *
 * This check reports whether or not a connection to the database can be established:
 *
 * [source]
 * ----
 * {@link examples.Examples#jdbc(io.vertx.ext.jdbc.JDBCClient, me.escoffier.vertx.healthchecks.HealthCheckHandler)}
 * ----
 *
 * === Service availability
 *
 * This check reports whether or not a service (here a HTTP endpoint) is available in the service discovery:
 *
 * [source]
 * ----
 * {@link examples.Examples#service}
 * ----
 *
 * === Event bus
 *
 * This check reports whether a consumer is ready on the event bus. The protocol, in this example, is a simple
 * ping/pong, but it can be more sophisticated. This check can be used to check whether or not a verticle is ready
 * if it's listening on a specific event address.
 *
 * [source]
 * ----
 * {@link examples.Examples#eventbus(io.vertx.core.Vertx, me.escoffier.vertx.healthchecks.HealthCheckHandler)}
 * ----
 *
 * == Authentication
 *
 * // TODO auth
 *
 */
@ModuleGen(name = "vertx-health-checks", groupPackage = "me.escoffier.vertx")
@Document(fileName = "index.adoc")
package me.escoffier.vertx.healthchecks;

import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.docgen.Document;
