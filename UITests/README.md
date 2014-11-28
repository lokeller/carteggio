Introduction
---------------------

This directory contains functional test cases for the application.

The test cases are standard Junit tests that run on the host. To test
the app the test case spins up one or more emulators and install the 
app on them.

Every time a new test is started the app configuration is cleared.

To communicate with the emulator the following project is used:

https://github.com/lokeller/multiuiautomator


Prerequisites
---------------------

You need to have installed x86 emulator images for android 4.4.2

How to execute tests
---------------------

0. Build the Carteggio App with ant (see main README)

1. First you need to import this directory as an eclipse project 
   (File -> Import -> General -> Existing Project )

2. Then you need to copy the file test.properties.default to 
   test.properties and set the values inside

3. Finally you can run any of the tests from eclipse by doing 
   right-click and on the test 
   ( Run as -> Junit Test -> Eclipse JUnit Launcher )
