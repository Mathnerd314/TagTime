To determine how you spend your time, TagTime randomly samples you. At random times it pops up and asks what you're doing right at that moment. You answer with tags.

For more on the idea behind this project, see [messymatters.com/tagtime](http://messymatters.com/tagtime).

# Usage:

0. Clone the repository on Github and build the project, or download and extract the zip file at http://www.mediafire.com/download.php?ndaaq5p3r6z7u45 (the latter is easier, but it may not always be up to date). Do not move the files around relative to one another, though feel free to move the directory they're in.
1. Before running the app, open usernames.txt and replace "yourusernamehere" with your Beeminder username.
2. Assuming you have Java installed, run the app by double-clicking TagTime.jar.
3. If you want to change your settings, exit the app by right-clicking the icon in the system tray. Find your settings file in the newly-created data folder. Update your settings (see SettingType.java for documentation), and start the app again when you're done. For the record, I do plan to make this easier, but don't hold your breath.
4. Answer the pings! (Always answer with what it caught you at right at that moment.)

# Credits:

TagTime was conceived of, designed, and [implemented](https://github.com/dreeves/TagTime) by Dreeves and Bethany Soule. This Java version was created by Joseph Cloutier (player_03).

The Java implementation uses [Quartz Scheduler](http://www.quartz-scheduler.org/), which is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) and created by Terracotta, Inc.

The Java implementation additionally uses [content](http://commons.apache.org/codec/) from the Apache Commons, also licensed under the Apache License 2.0.

The system tray icon comes from the [Silk icon set](http://www.famfamfam.com/lab/icons/silk/) by Mark James, available under a [Creative Commons Attribution 2.5 license](http://creativecommons.org/licenses/by/2.5/).