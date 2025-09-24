# main.py

import time
import bluetooth
import ble_uart
import display
import nfc_module
import ir_module

# globals
ble = None
current_mode = "IDLE"
current_submode = ""
last_ui_update = 0
last_connection_state = False
last_uid = None
last_ir_code = None

def handle_command(cmd):
    global current_mode, current_submode, last_ui_update

    print("CMD:", cmd)

    # 1) Combined SET_MODE + SUBMODE
    if cmd.startswith("SET_MODE:"):
        parts = cmd.split("|")
        current_mode   = parts[0].split(":", 1)[1]
        if len(parts) > 1 and parts[1].startswith("SUBMODE:"):
            current_submode = parts[1].split(":", 1)[1]
        else:
            current_submode = ""
        # update display: "Mode: RFID/READ" etc.
        display.set_mode(current_mode + ("/" + current_submode if current_submode else ""))
        display.show_idle()
        ble.send(b"OK")
        last_ui_update = time.ticks_ms()
        return

    # 2) RFID mode command handling
    if current_mode == "RFID":
        if current_submode == "READ":
            if cmd in ("read_nfc", "RFID_SCAN"):
                uid = nfc_module.read_uid()
                if uid:
                    ble.send(b"UID:" + uid.encode())
                    if cmd == "RFID_SCAN":
                        time.sleep_ms(100)
                        data = nfc_module.read_data_block()
                        if data:
                            ble.send(b"DATA:" + data.encode())
                        else:
                            ble.send(b"ERR:NoData")
                else:
                    ble.send(b"ERR:NoUID")
            else:
                ble.send(b"ERR:UnknownCommand")
        elif current_submode == "WRITE" and cmd.startswith("WRITE_NFC:"):
            data = cmd.split(":", 1)[1]
            ok = nfc_module.write_block(data)
            ble.send(b"WRITE_OK" if ok else b"WRITE_FAIL")
        elif current_submode == "EMIT" and cmd.startswith("EMIT_UID:"):
            data = cmd.split(":", 1)[1]
            ok = nfc_module.emit_uid(data)
            ble.send(b"EMIT_OK" if ok else b"EMIT_FAIL")
        else:
            ble.send(b"IGNORED:WrongSubmode")

    # 3) IR mode command handling
    elif current_mode == "IR":
        if current_submode == "READ" and cmd == "IR_READ":
            code = ir_module.get_last_code()
            if code:
                ble.send(b"IR_CODE:" + code.encode())
        elif cmd.startswith("IR_SEND:"):
            payload = cmd.split(":",1)[1]
            # if it's NEC or RAW, dispatch appropriately
            ok = False
            if payload.startswith("NEC"):
                ok = ir_module.send_nec(payload)
            else:
                ok = ir_module.send_ir(payload)
            ble.send(b"IR_OK" if ok else b"IR_FAIL")
        else:
            ble.send(b"IGNORED:WrongSubmode")

    else:
        ble.send(b"IGNORED:WrongMode")

    last_ui_update = time.ticks_ms()

# ──────────────────────────────────────────────────────────────────────
# setup BLE & display
display.set_ble_instance(None)
ble = ble_uart.BLEUART(handle_command)
display.set_ble_instance(ble)
display.show_idle()

# main loop
while True:
    now = time.ticks_ms()

    # 1) Drain one BLE command per cycle
    cmd = ble.get_next_command()
    if cmd:
        try:
            handle_command(cmd)
        except Exception as e:
            print("Command error:", e)

    # 2) Automatic background read: NFC
    if current_mode == "RFID" and current_submode == "READ":
        uid = nfc_module.read_uid()
        if uid and uid != last_uid:
            last_uid = uid
            if ble.is_connected():
                data = nfc_module.read_data_block() or "N/A"
                payload = b"UID:" + uid.encode() + b"|" + data.encode()
                ble.send(payload)
            last_ui_update = now

    # 3) Automatic background read: IR
    if current_mode == "IR" and current_submode == "READ":
        code = ir_module.read_ir()
        if code and code != last_ir_code:
            last_ir_code = code
            if ble.is_connected():
                ble.send(b"IR_CODE:" + code.encode())
            last_ui_update = now

    # 4) UI: connection state changes
    if ble.is_connected() != last_connection_state:
        last_connection_state = ble.is_connected()
        display.show_idle()
        last_ui_update = now

    # 5) Auto‑return to idle after timeout
    if time.ticks_diff(now, last_ui_update) > 3000:
        display.show_idle()

    time.sleep(0.2)
