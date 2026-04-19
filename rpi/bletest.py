import asyncio
from bleak import BleakScanner

async def main():
    seen = set()
    def cb(d, a):
        if d.address in seen: return
        seen.add(d.address)
        print(d.address, a.local_name, a.rssi)
        print("  svc:", a.service_data)
        print("  mfr:", a.manufacturer_data)
    async with BleakScanner(cb, adapter="hci1"):
        print("Scanning 20s...")
        await asyncio.sleep(20)

asyncio.run(main())
