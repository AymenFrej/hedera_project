-- Local demo identities only. Replace with real provisioning in non-demo environments.
INSERT INTO accounts (id, user_id, email, hedera_account_id, balance, status) VALUES
  ('acct_demo_user', 'user_demo', 'user@example.com', '0.0.demo-user', 0, 'MOCK'),
  ('acct_demo_admin', 'admin_demo', 'admin@example.com', '0.0.demo-admin', 0, 'MOCK'),
  ('acct_demo_auditor', 'auditor_demo', 'auditor@example.com', '0.0.demo-auditor', 0, 'MOCK'),
  ('acct_demo_platform', 'platform_demo', 'platform@example.com', '0.0.demo-platform', 0, 'MOCK');

INSERT INTO users (id, email, display_name, password_hash, role, account_id) VALUES
  ('user_demo', 'user@example.com', 'Demo User', '69d57ea4df5dc21af52559f9b8417de8f65c8a843cab060d7d1c2fe9dd45af30', 'USER', 'acct_demo_user'),
  ('admin_demo', 'admin@example.com', 'Demo Admin', 'a44ca5d29f6dab4320ab986479fa985b2d584b11a7da934f7e80bb1449913a07', 'ADMIN', 'acct_demo_admin'),
  ('auditor_demo', 'auditor@example.com', 'Demo Auditor', '76ce5b564fd53d180984b17dc86eb0dc3f46555163a9af6a3a74daa549786f7e', 'AUDITOR', 'acct_demo_auditor'),
  ('platform_demo', 'platform@example.com', 'Platform Service', '62564b7a58795588452d897cc3e881bb7016d207bd28c00391220ed7d46328ea', 'PLATFORM', 'acct_demo_platform');
