# ir_module.py

import time
from machine import Pin, PWM

# ==== CONFIGURATION ====
TX_PIN = 15    # GPIO for IR LED drive (through transistor)
RX_PIN = 14    # GPIO for IR receiver output

CARRIER_FREQ = 38000        # 38 kHz
CARRIER_DUTY = 32768        # 50% duty on 16‑bit PWM

MAX_PULSES = 1000
MIN_PULSES = 10
HEADER_TIMEOUT_US = 2_000_000   # 2 s
SILENCE_TIMEOUT_US = 50_000     # 50 ms

# ==== HARDWARE SETUP ====
_ir_pwm = PWM(Pin(TX_PIN))
_ir_pwm.freq(CARRIER_FREQ)
_ir_pwm.duty_u16(0)

_ir_rx = Pin(RX_PIN, Pin.IN, Pin.PULL_UP)

_last_code = ""  # buffer for the last read code


def send_ir(pulse_sequence: str) -> bool:
    """Transmit a raw IR pulse train (“CSV” of μs durations)."""
    try:
        pulses = [int(x) for x in pulse_sequence.split(",") if x]
    except:
        return False

    for i, us in enumerate(pulses):
        _ir_pwm.duty_u16(CARRIER_DUTY if (i % 2 == 0) else 0)
        time.sleep_us(us)
    _ir_pwm.duty_u16(0)
    return True


def read_ir() -> str:
    """
    Capture raw high/low durations (in μs) into a CSV string.
    Rejects bursts shorter than MIN_PULSES.
    """
    global _last_code
    t0 = time.ticks_us()
    # wait for start (falling edge)
    while _ir_rx.value() == 1:
        if time.ticks_diff(time.ticks_us(), t0) > HEADER_TIMEOUT_US:
            return ""  # timeout

    pulses = []
    last = time.ticks_us()
    while len(pulses) < MAX_PULSES:
        lvl = _ir_rx.value()
        # wait for toggle or silence timeout
        while _ir_rx.value() == lvl:
            if time.ticks_diff(time.ticks_us(), last) > SILENCE_TIMEOUT_US:
                break
        now = time.ticks_us()
        dt = time.ticks_diff(now, last)
        pulses.append(dt)
        last = now
        if dt > SILENCE_TIMEOUT_US:
            break

    if len(pulses) < MIN_PULSES:
        return ""  # too small

    # drop trailing silence
    if pulses[-1] > SILENCE_TIMEOUT_US:
        pulses.pop()

    raw_csv = ",".join(str(p) for p in pulses)
    _last_code = raw_csv
    return raw_csv


# ─── NEC Decoding Helpers ─────────────────────────────────────────────

def _decode_nec(pulses):
    if not (8000 < pulses[0] < 10000 and 4000 < pulses[1] < 5000):
        return None
    bits = []
    i = 2
    while i+1 < len(pulses) and len(bits)<32:
        high, low = pulses[i], pulses[i+1]
        i += 2
        if not (400 < high < 700):
            return None
        if 400 < low < 1000:
            bits.append(0)
        elif 1400 < low < 2000:
            bits.append(1)
        else:
            return None
    if len(bits) != 32:
        return None
    def b2byte(b): 
        v=0
        for idx,bit in enumerate(b):
            v |= (bit<<idx)
        return v
    addr, addr_inv = b2byte(bits[0:8]), b2byte(bits[8:16])
    cmd,  cmd_inv  = b2byte(bits[16:24]), b2byte(bits[24:32])
    if (addr^addr_inv)!=0xFF or (cmd^cmd_inv)!=0xFF:
        return None
    return addr, cmd

def read_nec() -> str:
    """
    Read raw pulses, then try NEC decode.
    Returns "NEC:AA:CC" or "RAW:…".
    """
    raw = read_ir()
    if not raw:
        return ""
    pulses = [int(x) for x in raw.split(",")]
    dec = _decode_nec(pulses)
    if dec:
        a,c = dec
        code = "NEC:{:02X}:{:02X}".format(a,c)
    else:
        code = "RAW:" + raw
    # buffer it
    global _last_code
    _last_code = code
    return code

def encode_nec(addr, cmd):
    # build NEC pulse list
    bits = []
    for byte in (addr, addr^0xFF, cmd, cmd^0xFF):
        for i in range(8):
            bits.append((byte>>i)&1)
    pulses = [9000,4500]
    for bit in bits:
        pulses += [562, 562 if bit==0 else 1687]
    pulses.append(562)  # footer
    return pulses

def send_nec(code_str: str) -> bool:
    """
    Transmit a NEC code string "NEC AA:BB".
    """
    try:
        _, hexes = code_str.split()
        a_s,c_s  = hexes.split(":")
        a, c = int(a_s,16), int(c_s,16)
    except:
        return False
    seq = ",".join(str(p) for p in encode_nec(a,c))
    return send_ir(seq)


def get_last_code() -> str:
    """
    Return the last buffered code (either NEC:… or RAW:…).
    """
    return globals().get("_last_code", "")
