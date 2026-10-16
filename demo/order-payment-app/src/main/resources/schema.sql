-- Dedicated demo tables preserve the existing demo-test2 tables, whose columns
-- do not match the current canonical DEC data-order.yaml table mapping.
CREATE TABLE IF NOT EXISTS dec_demo_user_info (
  u_id INT PRIMARY KEY,
  u_name VARCHAR(255),
  u_active INT,
  u_certified INT
);

CREATE TABLE IF NOT EXISTS dec_demo_order_info (
  o_id INT PRIMARY KEY,
  o_user_id INT,
  o_product_count INT,
  o_total_price DECIMAL(18, 2),
  o_total_amount DECIMAL(18, 2),
  o_status INT,
  o_pay_id INT,
  o_date DATE,
  o_remark VARCHAR(1000),
  o_type INT
);

CREATE TABLE IF NOT EXISTS dec_demo_order_detail_info (
  od_id INT PRIMARY KEY,
  od_order_id INT,
  od_user_id INT,
  od_product_id INT,
  od_product_name VARCHAR(255),
  od_product_price DECIMAL(18, 2),
  od_product_amount DECIMAL(18, 2),
  od_status INT
);

CREATE TABLE IF NOT EXISTS dec_demo_pay_info (
  p_id INT PRIMARY KEY,
  p_order_id INT,
  p_user_id INT,
  p_status INT,
  p_result_code INT
);

CREATE TABLE IF NOT EXISTS dec_demo_pay_detail_info (
  pd_id INT PRIMARY KEY,
  pd_pay_id INT,
  pd_product_id INT,
  pd_product_name VARCHAR(255),
  pd_pay_price DECIMAL(18, 2),
  pd_pay_amount DECIMAL(18, 2)
);

CREATE TABLE IF NOT EXISTS dec_demo_payment_error (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  result_code INT NOT NULL
);
