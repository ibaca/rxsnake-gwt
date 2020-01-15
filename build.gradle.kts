plugins {
    java
    id("org.ajoberstar.git-publish") version "3.0.0-rc.1"
}

group = "com.intendia"
version = "1.0-SNAPSHOT"
description = "Snake game in GWT using reactive programming style powered by RxJava GWT."

java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/google-snapshots/") }
    maven { url = uri("https://raw.githubusercontent.com/intendia-oss/rxjava-gwt/mvn-repo/") }
    mavenLocal()
}

dependencies {
    implementation("com.google.gwt:gwt-user:HEAD-SNAPSHOT")
    implementation("com.google.gwt:gwt-dev:HEAD-SNAPSHOT")
    implementation("com.intendia.gwt.rxgwt2:rxgwt:HEAD-SNAPSHOT")
    implementation("org.jboss.gwt.elemento:elemento-core:0.9.6-gwt2")
}

fun gwtClasspath() = sourceSets.main.get().run {
    files(java.srcDirs, resources.srcDirs, compileClasspath)
}

val gwtCompile = tasks.register<JavaExec>("gwtCompile") {
    dependsOn(tasks.classes)
    description = "GWT compiler"
    inputs.files(gwtClasspath())
    outputs.dir("$buildDir/gwtCompile/war/rxsnake")
    workingDir("$buildDir/gwtCompile")
    doFirst { workingDir.mkdirs() }
    main = "com.google.gwt.dev.Compiler"
    classpath(gwtClasspath())
    args = listOf("-sourceLevel", "11", "-draftCompile", "-failOnError", "rxsnake.RxSnake")
}

tasks.assemble {
    dependsOn(gwtCompile)
}

tasks.register<JavaExec>("gwtServe") {
    dependsOn(tasks.classes)
    description = "GWT serve"
    workingDir("$buildDir/gwtServe")
    doFirst { workingDir.mkdirs() }
    main = "com.google.gwt.dev.DevMode"
    classpath(gwtClasspath())
    args = listOf("-sourceLevel", "11", "-failOnError", "rxsnake.RxSnake")
}

gitPublish {
    repoUri.set("git@github.com:ibaca/rxsnake-gwt.git")
    branch.set("gh-pages")
    contents {
        from(gwtCompile)
    }
}
