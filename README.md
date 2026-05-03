<p align="center">
 <img src="./ps5loader.png" width="128" />
</p>
<h1 align="center">PS5 BD-JB Autoloader</h1>
<h3 align="center">Fork of <a href="https://github.com/Gezine/BD-UN-JB">BD-UN-JB</a></h3>
&nbsp;
<p align="center">Automatically loads your .elf, .bin and .jar payloads.<br>Supports PS5 firmwares up to 7.61. <br><b>Note:</b> If you are already jailbroken on a higher firmware (up to 12.00), you can make it work by using the `bdj_unpatch` payload.

<p align="center">
    <b>Other Autoloaders:</b><br>
    <a href="https://github.com/itsPLK/ps5-y2jb-autoloader">PS5 Y2JB Autoloader</a> | 
    <a href="https://github.com/itsPLK/ps5-lua-autoloader">PS5 Lua Autoloader</a>
</p>

<p align="center">
 <img src="./bdjb_screenshot.png" width="600" />
</p>


## How to Use

- Create a directory named `ps5_autoloader`.
- Inside this directory, place your `.elf`, `.bin`, and `.jar` files, and an `autoload.txt` file.
  - In `autoload.txt`, list the files you want to load, one filename per line.
  - Filenames are case-sensitive — ensure each name exactly matches the file.
  - You can add lines like `!1000` to make the loader wait 1000 ms before sending the next payload.
  - You can add lines like `@ message` to show a notification on the PS5.
  - Do NOT include `elfldr.elf` in `autoload.txt` unless you want to override the bundled version; it is loaded automatically when needed for ELF/BIN payloads.
- Put the `ps5_autoloader` directory in one of these locations (priority order - highest first):
  - Root of a USB drive
  - Internal drive: `/data/ps5_autoloader`
  - Root of the disc itself.


## Setup Instructions

This autoloader is deployed via a BD-R disc.

1. Download the **PS5 BD-JB Autoloader ISO** from the [Releases](https://github.com/itsPLK/ps5-bdjb-autoloader/releases) page.
2. Burn the ISO to a BD-R(E) disc using software like `ImgBurn` (use UDF 2.50 filesystem).
3. Insert the disc into the PS5 and launch it from the "Media" tab.

*Note: Since this is a disc-based loader, updates to the loader itself require burning a new ISO. However, your payloads on USB or internal storage can be updated at any time.*


## Additional Info

<Details>
<Summary><i>How to use custom ELF Loader version?</i></Summary>

By default, the autoloader uses a bundled version of **elfldr** that only accepts connections from the PS5 itself (localhost). This improves security by preventing other devices on your network from sending payloads to your console.

If you want to use a "normal" ELF Loader that allows sending payloads from any device:
1. Place your `elfldr.elf` in the `ps5_autoloader` directory.
2. Add `elfldr.elf` as the first entry in your `autoload.txt`.
</Details>

<Details>
<Summary><i>Payload Manager integration</i></Summary>

The autoloader includes **Payload Manager**. Using it is the most reliable way to load etaHEN/kstuff, as it closes the Disc app before sending the payloads. To use it, make `pldmgr.elf` the **only** item in your `autoload.txt`.
</Details>


## Credits

* **[TheFlow](https://github.com/theofficialflow)** — BD-JB documentation & native code execution sources.  
* **[hammer-83](https://github.com/hammer-83)** — PS5 Remote JAR Loader reference.  
* **[john-tornblom](https://github.com/john-tornblom)** — [BDJ-SDK](https://github.com/john-tornblom/bdj-sdk) and [ps5-payload-sdk](https://github.com/ps5-payload-dev/sdk/) used for compilation.  
* **[kuba--](https://github.com/kuba--)** — [zip](https://github.com/kuba--/zip) used for bdj_unpatch elf payload.  

## Disclaimer

This tool is provided as-is for research and development purposes only. Use at your own risk. The developers are not responsible for any damage, data loss, or consequences resulting from the use of this software.

## License

This project is licensed under the GPL-3.0 License.

The original base code remains under its original MIT License (see LICENSE-MIT).
All unique modifications and additions in this project are licensed under GPL-3.0.

## Donate
- [donate to PLK](DONATE.md)
