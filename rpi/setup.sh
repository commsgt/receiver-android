#!/bin/bash
# OpenDroneID RPi4 setup script
# Run once as root on a fresh Raspberry Pi OS install.

set -e

echo "=== OpenDroneID RPi4 Receiver Setup ==="

# 1. System packages
apt-get update
apt-get install -y \
    openjdk-17-jre-headless \
    maven \
    libpcap-dev \
    bluez \
    gpsd \
    gpsd-clients \
    aircrack-ng

# 2. Enable SPI/I2C/Serial for GPS (if using UART GPS)
echo "Enabling UART for GPS..."
grep -qxF 'enable_uart=1' /boot/config.txt || echo 'enable_uart=1' >> /boot/config.txt
grep -qxF 'dtoverlay=disable-bt' /boot/config.txt || echo 'dtoverlay=disable-bt' >> /boot/config.txt

# 3. Configure gpsd
echo "Configuring gpsd..."
cat > /etc/default/gpsd <<EOF
DEVICES="/dev/ttyAMA0"
GPSD_OPTIONS="-n"
USBAUTO="true"
EOF
systemctl enable gpsd
systemctl start gpsd

# 4. Create monitor-mode setup script for Alfa adapter
cat > /usr/local/bin/start-monitor.sh <<'EOF'
#!/bin/bash
# Put Alfa adapter in monitor mode. Run before starting the receiver.
IFACE=${1:-wlan1}
echo "Setting $IFACE to monitor mode..."
ip link set $IFACE down
iw dev $IFACE set type monitor
ip link set $IFACE up
iw dev $IFACE set channel 6
echo "Monitor interface ready: ${IFACE}"
EOF
chmod +x /usr/local/bin/start-monitor.sh

# 5. Set BLE adapter to passive scanning friendly mode
echo "Configuring BlueZ..."
cat > /etc/bluetooth/main.conf <<EOF
[Policy]
AutoEnable=true
EOF
systemctl restart bluetooth

# 6. Build the JAR
echo "Building receiver JAR..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
mvn -q package -DskipTests
echo "JAR built: target/receiver-rpi-1.0-SNAPSHOT.jar"

# 7. Create run script
cat > /usr/local/bin/run-receiver.sh <<'EOF'
#!/bin/bash
# Start OpenDroneID receiver with BLE + WiFi + GPS logging
JAR="/opt/opendroneID/receiver-rpi.jar"
WIFI_IFACE=${1:-wlan1}

# Put WiFi adapter in monitor mode
/usr/local/bin/start-monitor.sh $WIFI_IFACE

# Run receiver (needs sudo for pcap raw socket)
exec sudo java \
    -Dorg.pcap4j.core.pcapLibName=libpcap.so \
    -jar "$JAR" \
    --wifi "${WIFI_IFACE}mon" \
    --log
EOF
chmod +x /usr/local/bin/run-receiver.sh

echo ""
echo "=== Setup complete ==="
echo ""
echo "Usage:"
echo "  1. Put Alfa adapter in monitor mode:  sudo start-monitor.sh wlan1"
echo "  2. Run receiver:                      sudo java -jar target/receiver-rpi-1.0-SNAPSHOT.jar --wifi wlan1"
echo "  3. Optional flags:"
echo "       --no-ble          skip BLE scanning"
echo "       --no-wifi         skip WiFi beacon scanning"
echo "       --log             write CSV log file"
echo "       --wifi <iface>    WiFi interface (default: wlan0mon)"
echo ""
echo "  GPS: connect GPS to /dev/ttyAMA0 (UART pins 14/15)"
echo "       or USB GPS (gpsd will auto-detect)"
