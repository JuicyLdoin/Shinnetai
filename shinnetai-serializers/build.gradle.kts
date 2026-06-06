dependencies {
    implementation(project(":shinnetai-proxy"))

    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    compileOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-smile:2.18.2")
    compileOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.18.2")
    compileOnly("org.msgpack:jackson-dataformat-msgpack:0.9.8")
    compileOnly("com.google.flatbuffers:flatbuffers-java:24.3.25")
}
