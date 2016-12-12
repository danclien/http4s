package org.http4s
package prometheus
package server

import scala.collection.JavaConverters._

import scalaz.concurrent.Task

import io.prometheus.client._
import org.http4s.dsl._
import org.http4s.Http4sSpec
import org.http4s.Uri.uri

class MetricsSpec extends Http4sSpec {
  val service = HttpService {
    case GET -> Root / "ping" => Ok("pong")
    case GET -> Root / "nope" => Task.async[Response] { cb => }
  }

  "Metrics" should {
    "create a histogram per method and status" in {
      val registry = new CollectorRegistry
      val service0 = Metrics("foo", registry = registry)(service)
      service0.run(Request(Method.GET, uri("/ping"))).run
      registry.getSampleValue("foo_http_request_time_seconds_count", Array("method", "status"), Array("GET", "2xx")) must_!= null
      registry.getSampleValue("foo_http_request_time_seconds_bucket", Array("method", "status", "le"), Array("GET", "2xx", "+Inf")) must_!= null
      service0.run(Request(Method.POST, uri("/ping"))).run
      registry.getSampleValue("foo_http_request_time_seconds_count", Array("method", "status"), Array("POST", "4xx")) must_!= null
      registry.getSampleValue("foo_http_request_time_seconds_bucket", Array("method", "status", "le"), Array("POST", "4xx", "+Inf")) must_!= null
    }

    "counts active requests" in {
      val registry = new CollectorRegistry
      val service0 = Metrics(registry = registry)(service)
      // This one never completes, so we can measure it as active
      service0.run(Request(Method.GET, uri("/nope"))).runAsync { _ => }
      registry.getSampleValue("http_requests_active") must_== 1.0
      // This one completes, so we can measure that the gauge decreased
      service0.run(Request(Method.GET, uri("/ping"))).run
      registry.getSampleValue("http_requests_active") must_== 1.0
    }

    "respects the method whitelist" in {
      val registry0 = new CollectorRegistry
      val service0 = Metrics(registry = registry0)(service)
      service0.run(Request(Method.GET, uri("/ping"))).run
      registry0.getSampleValue("http_request_time_seconds_count", Array("method", "status"), Array("GET", "2xx")) must_!= null

      val registry1 = new CollectorRegistry
      val service1 = Metrics(registry = registry1, whitelistedMethods = Set.empty)(service)
      service1.run(Request(Method.GET, uri("/ping"))).run
      registry1.getSampleValue("http_request_time_seconds_count", Array("method", "status"), Array("other", "2xx")) must_!= null
    }
  }
}