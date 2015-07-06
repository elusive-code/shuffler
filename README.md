# Shuffler

Shuffler is a plugin for IntelliJ Idea Community Edition, that performs java non-destructive source-code obfuscation.

Plugin adds "Shuffle" action to "Refactoring menu".
This action shuffles names of private and protected classes, methods and variables in current project.

To do so it generates Markov chains from sources in project,
then generates new names based on corresponding Markov chain.

It also removes some comments.

## Dependencies
IntelliJ Idea 11 Community Edition or newer.

## Installation

1. "File" -> "Settings" -> "Plugins"
2a. "Browser Repositories" and find 'Shuffler' plugin
2b. "Install plugin from disk" and choose path to shuffler.jar

## License

GNU General Public License v 3.0

Copyright (C) 2015, LLC Open Code.

See LICENSE for details.