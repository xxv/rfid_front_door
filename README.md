RFID Front Door
===============

Using their existing RFID tag, a person can unlock a door if they're in the
right group. It's easy to switch which current group is allowed access, so
groups can be set based on who's allowed in at a given point in time.

For example, a close group of friends who have regular parties could all be
added to a group, which could be switched to on when that party is happening.
Or one could use it to only allow a limited number people in during the
evening, but allow more in during the day.

I live near Boston and our public transit system uses these cards for regular
users of the subway. This means that everyone I know has one already in their
pocket, so it makes it very easy to give any of my friends access. Also, the
city gives out the cards for free, so it's easy to get your hands on unused
ones just by looking for them in the subway stations.

Below are some details if you'd like to build one yourself. I was going for
style, compactness, and better control over a quick hack, so there are many
details here that can be omitted if desired.

RFIDs
-----

The system was designed to work with 13.56MHz IEC 14443 Mifare Classic and
other similar RFID tags, but one could use another reader or tag type if
desired.

Up to 50 tags can be stored on an atmega168 chip or 100 for a mega328 (check
the printing on the main chip on your Arduino board).

Groups
------

There are seven groups which function as an access control list. Only one group
can be active at a time and the currently active group's number is displayed on
the LED display.

Each RFID can be added to any of those groups.

All the RFIDs and groups are remembered in EEPROM, so they'll survive power
cycling.

The currently active group, however, is *not* stored in EEPROM, so it will
revert to group 1 if the power is cycled. You should ensure that group 1 is a
safe group to be active in this case.

Physical Interface
------------------

The setup has a 7-segment display and a single button. The 7-segment display
shows the active group number and the button advances through the groups.

To add a card to a group, long-press the button until it says, "scan card".
Once you tap the card on the reader, it'll be either added or removed (if it's
already in the group).

Serial Control Interface
------------------------

Besides the physical interface, there is a textual control interface that can
be used over USB or Bluetooth. The Android app speaks this interface over
Bluetooth to manipulate the groups and activate the relay.


###Commands

The command language is simple: just one character followed by some arguments
and terminated with a newline. There is no whitespace between the command and
its arguments, although it's possible some commands will ignore it. It supports
CR, LF, or CRLF, so it should be usable from a simple terminal app or the
Arduino serial console.

#### 'l' command

List all the records.

Syntax: 

    l <newline>

Output is in the following format: 

> *group* &lt;tab> *ID*

*group* is a bitfield of all the groups the ID is in. Group 1 is the
least significant bit, group 7 is the second-to-most significant bit.

*ID* is the ID, chunked into bytes, printed in hex and separated with ":"s. Eg.:

    00:12:23:43:8e

#### 'c' command

Erases all records. Doesn't prompt for confirmation, so be careful with it.

Syntax:

    c <newline>

#### 'a' command

Add the given ID to the currently active group.

Syntax:

    a <id> <newline>

Adds an ID. &lt;id> should be conveyed in hex, much like the output of l
(although the ":" are optional). This adds the ID to the currently active
group.

#### 'A' command

Adds the next scanned card to the current group.

Syntax:

    A <newline>

#### 'd' command

Deletes the ID from all groups.

Syntax:

    d <id> <newline>

&lt;id> is in the same format as the "a" command.

#### 'r' command

Remove the ID from the current group.

Syntax:

    r <id> <newline>

#### 'g' command

Sets the currently active group.

Syntax:

    g <group> <newline>

&lt;group> is "1"-"7" and can be omitted to just retrieve the current group.

Prints the current group.

#### 'o' command

Opens the door.

Syntax:

    o <id> <newline>

#### 'v' command

Prints version info.

Syntax:

    v <newline>

Android App
-----------

There's an Android app included in this project to allow one to add/remove
cards from the groups, switch the currently active group, and trigger the
relay. If you have NFC on your phone, it lets you scan cards from the phone to
add them directly to the list.

Hardware
--------

* Arduino (or similar)
* [Sparkfun Arduino ProtoShield][protoshield]
* 7-segment LED display
* CD4094B or similar 8-Stage Shift-and-Store Bus Register
* [Seeed Studio RFID reader][rfidreader]
* Any non-latching [Relay][relay]
* [Bluetooth Transceiver][bluetooth] (optional)

I built this with some parts I had lying around, but I think they're actually
fairly well suited for building similar setups. The shift register makes
driving the 7-segment display easier, but isn't necessary - one could probably
find enough spare pins or use an LED driver or some such.

I decided to use the Grove relay board because I wanted to have the relay in a
different location than the main interface board. Obviously this is overkill
for many setups and a simple relay (+ transistor) would suffice.

Housing
-------

One of the things that I love is hardware with beautiful housing. So I
attempted to create a wooden housing based on examples of ornately decorated
objects that I found. I'm going to be looking at this every day, so I wanted it
to be pleasant and not as impersonal as so much technology feels.

There are two housings: one to hold the Arduino + ProtoShield, RFID reader
board and Bluetooth transceiver; and another to decorate the RFID reader
itself. Both are intended to be lasercut.

The colors in the housing SVG files represent the following:

* Red: cut
* Green: medium raster etch
* Blue: deep raster etch (to fit the antenna)
* other colors: informational; don't cut

The main box was made with [BoxMaker][], which is an excellent tool for making
laser cut boxes that fit together nicely.

[protoshield]: http://www.sparkfun.com/products/7914
[rfidreader]: http://www.seeedstudio.com/wiki/index.php?title=13.56Mhz_RFID_module_-_IOS/IEC_14443_type_a
[bluetooth]: http://www.sparkfun.com/products/9358
[relay]: https://www.seeedstudio.com/depot/grove-relay-p-769.html?cPath=156_160
[BoxMaker]: http://www.rahulbotics.com/personal-projects/boxmaker/

