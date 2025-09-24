# nfc_module.py
from machine import Pin, SPI
import NFC_PN532 as nfc
import display
import time

# SPI pins
SCK_PIN = 18
MOSI_PIN = 19
MISO_PIN = 16
CS_PIN   = 17

spi = SPI(0, baudrate=1000000, sck=Pin(SCK_PIN), mosi=Pin(MOSI_PIN), miso=Pin(MISO_PIN))
cs  = Pin(CS_PIN, Pin.OUT)
cs.on()

pn532 = nfc.PN532(spi, cs)
try:
    ic, ver, rev, support = pn532.get_firmware_version()
    print("PN532 v{}.{}".format(ver, rev))
    pn532.SAM_configuration()
except Exception as e:
    print("NFC init error:", e)
    display.show_message("NFC Init Err")

def read_uid():
    print("Reading NFC...")
    uid = pn532.read_passive_target(timeout=0.5)
    if uid:
        uid_str = "-".join("{:02X}".format(b) for b in uid)
        print("NFC UID:", uid_str)
        display.show_nfc(uid_str)
        return uid_str
    return None

def emit_uid(uid_str):
    print("EMITTING:", uid_str)
    return True

def write_block(data: str, block: int = 4) -> bool:
    uid = pn532.read_passive_target(timeout=0.5)
    if not uid:
        print("No NFC card found.")
        display.show_message("No NFC")
        return False

    uid_str = "-".join("{:02X}".format(b) for b in uid)
    print("Found card with UID:", uid_str)

    # Try to detect card type by UID length or firmware support
    # MIFARE Classic tags typically have 4-byte UID, NTAG and Ultralight usually 7 bytes
    is_mifare_classic = (len(uid) == 4)

    # Prepare data
    if is_mifare_classic:
        raw = data.encode("utf-8")[:16]
        if len(raw) < 16:
            raw += b"\x00" * (16 - len(raw))
    else:
        # NTAG/Ultralight supports only 4 bytes per block/page
        raw = data.encode("utf-8")[:4]
        if len(raw) < 4:
            raw += b"\x00" * (4 - len(raw))

    if is_mifare_classic:
        # MIFARE Classic write with authentication
        try:
            if not pn532.mifare_classic_authenticate_block(uid, block, 0x60, b"\xFF" * 6):
                print("Authentication failed.")
                display.show_message("Auth FAIL")
                return False
        except Exception as e:
            print("Auth exception:", e)
            display.show_message("Auth Err")
            return False

        try:
            print("Writing raw bytes:", raw.hex())    #here
            pn532.mifare_classic_write_block(block, raw)
        except Exception as e:
            print("Exception during write:", e)
            display.show_message("Write Err")
            return False

    else:
        # NTAG/Ultralight write (no auth needed)
        try:
            pn532.ntag2xx_write_block(block, raw)
        except Exception as e:
            print("Exception during NTAG write:", e)
            display.show_message("Write Err")
            return False

    print("Write successful.")
    display.show_message("Write OK")
    return True

# nfc_module.py (append at bottom)

def read_data_block(block: int = 4) -> str:
    uid = pn532.read_passive_target(timeout=0.5)
    if not uid:
        print("No NFC card found.")
        return ""

    uid_str = "-".join("{:02X}".format(b) for b in uid)
    print("Reading data from UID:", uid_str)

    try:
        if not pn532.mifare_classic_authenticate_block(uid, block, 0x60, b"\xFF" * 6):
            print("Authentication failed.")
            return ""
    except Exception as e:
        print("Auth exception:", e)
        return ""

    try:
        block_data = pn532.mifare_classic_read_block(block)
        if block_data:
            print("Raw block data:", block_data.hex())         #here
            decoded = block_data.decode('utf-8').strip()
            print("Block data:", decoded)
            return decoded
    except Exception as e:
        print("Read error:", e)

    return ""
