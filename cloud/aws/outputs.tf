locals {
  public_ip = var.static_ip ? aws_eip.gaming[0].public_ip : aws_instance.gaming.public_ip
}

output "instance_id" {
  value = aws_instance.gaming.id
}

output "public_ip" {
  value = local.public_ip
}

output "web_ui" {
  value = "https://${local.public_ip}:47990  (via VPN: https://<vpn-ip>:47990)"
}

output "rdp" {
  value = "mstsc /v:${local.public_ip}   user: Administrator"
}

output "admin_password" {
  value     = rsadecrypt(aws_instance.gaming.password_data, tls_private_key.rdp.private_key_pem)
  sensitive = true
}

output "region" {
  value = var.region
}
