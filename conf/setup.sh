
# user
sudo useradd -s /bin/bash -d /home/ivan/ -m -G sudo ivan
sudo passwd ivan


# setup caddy
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy


# iptables
sudo iptables-persistent
sudo iptables -I INPUT -p tcp -m tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp -m tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save


# logs
tail -n 100 -f teleward/logs/teleward.log


# webhook
curl https://api.telegram.org/bot$TELEGRAM_TOKEN/getWebhookInfo | jq
curl -F "url=https://$DOMAIN/telegram/webhook" https://api.telegram.org/bot$TELEGRAM_TOKEN/setWebhook | jq
curl -X POST https://api.telegram.org/bot$TELEGRAM_TOKEN/deleteWebhook | jq
