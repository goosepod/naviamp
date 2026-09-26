# Naviamp Connect across a Tailnet

Automatic Connect discovery uses local DNS-SD/mDNS. Routed Tailnets normally carry the TCP connection but do not forward that discovery traffic. Use a manual address when the target does not appear under **Find Naviamp devices**.

1. Put the controller and playback target on the same Tailnet. Confirm that the controller can reach the target's Tailnet IP or MagicDNS hostname. The target's firewall and Tailnet policy must allow its Connect TCP listener.
2. On the target, open **Settings → Controllers** and show the pairing code. Note the **Manual connection port** shown with it. The target's Tailnet app supplies its IP or hostname.
3. On the controller, open **Settings → Controllers**. Enter `host:port` or `[IPv6 address]:port`, select **Pair at address**, then enter the six-digit code shown on the target. Approve the request on the target if prompted.
4. If a trusted target changes address or its port changes after an app restart, enter the new address and select **Reconnect to [device] at address**. Naviamp verifies the saved device identity and reconnect credential before accepting the new endpoint. If verification fails, inspect the target identity or pair again.

An address supplies a route only. Pairing still requires the code exchange, target approval, signed identity proof, and confirmation; reconnect still pins the stored identity. Manual entry does not change automatic discovery or same-LAN pairing.
