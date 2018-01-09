

if [[ $(/usr/bin/id -u) -ne 0 ]]; then
    echo "Not running as root"
    exit
fi

# General update
apt update

# Installation
chmod +x /home/pi/nfcbluetooth/bin/nfcbluetooth

# NFC
apt install libpcsclite1 pcscd
dpkg -i ./acs.deb
/etc/init.d/pcscd restart

# Boot
echo "JAVA_OPTS=-Dsun.security.smartcardio.library=/usr/lib/arm-linux-gnueabihf/libpcsclite.so.1" >> /etc/environment
echo '@reboot sleep 3;export JAVA_OPTS=-Dsun.security.smartcardio.library=/usr/lib/arm-linux-gnueabihf/libpcsclite.so.1;/home/pi/nfcbluetooth/bin/nfcbluetooth' >> /home/pi/sucron.txt
crontab /home/pi/sucron.txt

# Bluetooth
apt install libbluetooth-dev
# cp ./libbluecove_arm.so /user/lib/libbluecove_arm.so
sed -i 's,ExecStart=/usr/lib/bluetooth/bluetoothd,ExecStart=/usr/lib/bluetooth/bluetoothd/ -C,' /lib/systemd/system/bluetooth.service
chmod 777 /var/run/sdp
echo 'PRETTY_HOSTNAME=pi-nfc-bluetooth-' > /etc/machine-info
echo $(RANDOM % 1000) >> /etc/machine-info
systemctl daemon-reload
service bluetooth restart

# Run program now
hciconfig hci0 piscan #Make discoverable
export JAVA_OPTS=-Dsun.security.smartcardio.library=/usr/lib/arm-linux-gnueabihf/libpcsclite.so.1
/home/pi/nfcbluetooth/bin/nfcbluetooth