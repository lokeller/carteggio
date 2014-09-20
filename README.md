Introduction
----------------

Carteggio is a mobile email client with a UI optimized for interactive conversations.

For more information see: http://carteggio.ch

How to build
---------------

To build this project you have two options:

1. Use the Android Development Tools (ADT) plugin for eclipse. You can obtain it
   from https://developer.android.com/tools/sdk/eclipse-adt.html

   To setup the build you can use the following procedure:

     1. Create a new Eclipse workspace
     2. Go to Import -> Git -> Projects from Git
     3. Select import from URI
     4. Insert the following URI: https://github.com/carteggio/carteggio.git or
        if you plan to contribute go to github.com/carteggio/carteggio and fork
        the project, then insert the URI of your fork.
     5. Select the master branch
     6. Select a location where you will clone the repository (this should be 
        outside the workspace directory)
     7. Select "Import existing projects"
     8. Select the Carteggio project
     9. Now you are ready to build the project as described in :
          https://developer.android.com/tools/building/building-eclipse.html

2. You can compile the project using ant and the Android Development Kit that
   can be obtained from https://developer.android.com/sdk/installing/index.html?pkg=tools

     1. Clone the project locally:

         git clone https://github.com/carteggio/carteggio.git

     2. Update the project files:

         cd carteggio/Carteggio
         path/to/sdk/tools/android update project -p .

     3. Execute the build script:

         ant debug
