CREATE DATABASE todo_list;

USE todo_list;

CREATE TABLE user(
                     email VARCHAR(50) PRIMARY KEY,
                     name VARCHAR(100) NOT NULL,
                     password VARCHAR(25) NOT NULL
);

CREATE TABLE item(
                     id INT PRIMARY KEY AUTO_INCREMENT,
                     user_email VARCHAR(50) NOT NULL,
                     description VARCHAR(150) NOT NULL,
                     state ENUM('DONE', 'NOT_DONE'),
                     CONSTRAINT fk_user FOREIGN KEY (user_email) REFERENCES user(email)
);