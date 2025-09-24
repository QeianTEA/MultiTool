# display.py
from machine import I2C, Pin
import ssd1306

# I2C setup
i2c = I2C(0, scl=Pin(5), sda=Pin(4), freq=400000)
oled = ssd1306.SSD1306_I2C(128, 64, i2c)

_ble = None
_current_mode = "IDLE"
_current_submode = ""

def set_ble_instance(ble):
    global _ble
    _ble = ble

def set_mode(mode_str):
    global _current_mode, _current_submode
    if "/" in mode_str:
        _current_mode, _current_submode = mode_str.split("/",1)
    else:
        _current_mode, _current_submode = mode_str, ""

def show_idle():
    oled.fill(0)
    oled.text("MultiTool " + ("BT" if _ble and _ble.is_connected() else "--"), 0, 0)
    oled.text("Mode: " + _current_mode, 0, 10)
    if _current_submode:
       oled.text(_current_submode, 0, 20)
    oled.text("Waiting...", 0,  30)
    oled.show()

def show_nfc(uid):
    oled.fill(0)
    oled.text("MultiTool " + ("BT" if _ble and _ble.is_connected() else "--"), 0, 0)
    oled.text("Mode: " + _current_mode, 0, 10)
    if _current_submode:
        oled.text(_current_submode, 0, 20)
    oled.text("Tag:", 0,  30)
    oled.text(uid, 0,  40)
    oled.show()

def show_message(msg):
    if msg.startswith("SET_MODE:") or msg.startswith("SET_SUBMODE:"):
        return
    oled.fill(0)
    oled.text("MultiTool " + ("BT" if _ble and _ble.is_connected() else "--"), 0, 0)
    oled.text("Mode: " + _current_mode, 0, 10)
    if _current_submode:
        oled.text(_current_submode, 0, 20)
    oled.text("Cmd:", 0,  30)
    oled.text(msg, 0,  40)
    oled.show()
