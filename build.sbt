name := "monix-lab"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.13"
lazy val monixVersion = "2.3.3"
resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("ovotech", "maven")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
	"io.monix" %% "monix-reactive" % monixVersion,
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "io.monix" %% "monix-nio" % "0.0.3",
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0"
  )

