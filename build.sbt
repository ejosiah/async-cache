name := "async-cache"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.12" % "2.5.6",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.typesafe.akka" % "akka-testkit_2.12" % "2.5.6" % "test",
  "org.scalamock" % "scalamock-scalatest-support_2.12" % "3.6.0" % "test"
)

libraryDependencies ++= Seq(
  "org.pegdown" % "pegdown" % "1.6.0" % Test,
  "org.scalatestplus.play" % "scalatestplus-play_2.12" % "3.1.2" % "test"
)

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports/html"),
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html")
)