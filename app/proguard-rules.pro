# protobuf-lite generated messages are accessed reflectively by the lite runtime.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
