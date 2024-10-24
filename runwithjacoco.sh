#!/bin/zsh

#junit standaloneの通常起動
#java -cp build/classes/java/main -jar ./locallib/junit-platform-console-standalone-1.10.0.jar -cp ./.probe_test_classes --select-method=src4test.SampleTest#sample2

#junit standalone に jacocoagentをつけて起動
#java    -jar /Users/ezaki/Desktop/tmp/junit-platform-console-standalone-1.10.0.jar -cp /Users/ezaki/IdeaProjects/proj4test/build/classes/java/main:/Users/ezaki/IdeaProjects/MyFaultFinder/.probe_test_classes --scan-classpath

#junit standalone に jacocoagentをつけて起動 (メソッド指定)
java -javaagent:./locallib/jacocoagent.jar=destfile=./.jacoco_exec_data/test.exec -jar ./locallib/junit-platform-console-standalone-1.10.0.jar -cp /Users/ezaki/IdeaProjects/proj4test/build/classes/java/main:./.probe_test_classes --select-method demo.SortTest#test1
