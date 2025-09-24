# ble_uart.py
import bluetooth
from micropython import const
from ble_advertising import advertising_payload
import display
import time

# IRQ constants
_IRQ_CENTRAL_CONNECT    = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)
_IRQ_GATTS_WRITE        = const(3)

# Nordic UART UUIDs
_UART_UUID    = bluetooth.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
_UART_TX      = (bluetooth.UUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"), bluetooth.FLAG_NOTIFY)
_UART_RX      = (bluetooth.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"), bluetooth.FLAG_WRITE)
_UART_SERVICE = (_UART_UUID, (_UART_TX, _UART_RX))


class BLEUART:
    def __init__(self, ble, on_command, name="MultiTool"):
        self._ble = ble
        self._on_command = on_command
        self._ble.active(True)
        self._ble.irq(self._irq)
        ((self._tx_handle, self._rx_handle),) = self._ble.gatts_register_services((_UART_SERVICE,))
        self._connections = set()
        self._name = name

        # —— buffering state —— 
        self._recv_buffer = b""
        self._command_buffer = []  # queue of complete lines

        self._advertise()

    def _irq(self, event, data):
        if event == _IRQ_CENTRAL_CONNECT:
            conn_handle, _, _ = data
            self._connections.add(conn_handle)
            print("BLE connected")
            display.show_idle()

        elif event == _IRQ_CENTRAL_DISCONNECT:
            conn_handle, _, _ = data
            self._connections.discard(conn_handle)
            print("BLE disconnected")
            display.show_idle()
            self._advertise()

        elif event == _IRQ_GATTS_WRITE:
            _, value_handle = data
            if value_handle == self._rx_handle:
                chunk = self._ble.gatts_read(self._rx_handle) or b""
                # Append to buffer, look for lines ending in '\n'
                self._recv_buffer += chunk
                while b"\n" in self._recv_buffer:
                    line, self._recv_buffer = self._recv_buffer.split(b"\n", 1)
                    try:
                        cmd = line.decode("utf-8").strip()
                    except UnicodeError:
                        print("BLE decode error on chunk:", line)
                        continue
                    print("Received full cmd:", cmd)
                    display.show_message(cmd)
                    # queue it for the main loop to handle
                    self._command_buffer.append(cmd)

    def get_next_command(self):
        """Pop one complete command (without newline)."""
        if self._command_buffer:
            return self._command_buffer.pop(0)
        return None

    def is_connected(self):
        return bool(self._connections)
    
    def send(self, data: bytes):
        """Send a complete message, appending newline, split into 20-byte BLE chunks."""
        MAX_CHUNK = 20  # Default BLE notification size
        message = data + b'\n'  # ← Ensure complete logical message ends with newline

        for conn in self._connections:
            try:
                for i in range(0, len(message), MAX_CHUNK):
                    chunk = message[i:i + MAX_CHUNK]
                    self._ble.gatts_notify(conn, self._tx_handle, chunk)
                    time.sleep_ms(10)  # Avoid BLE congestion
            except Exception as e:
                print("Notify error:", e)

    def _advertise(self, interval_us: int = 500_000):
        adv = advertising_payload(services=[_UART_UUID])
        rsp = advertising_payload(name=self._name)
        self._ble.gap_advertise(interval_us, adv_data=adv, resp_data=rsp, connectable=True)
