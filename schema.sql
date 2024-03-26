CREATE TABLE `site` (
  `id` int NOT NULL AUTO_INCREMENT,
  `last_error` text,
  `name` varchar(255) NOT NULL,
  `status` enum('FAILED','INDEXED','INDEXING') NOT NULL,
  `status_time` datetime(6) NOT NULL,
  `url` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `page` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` int NOT NULL,
  `content` mediumtext NOT NULL,
  `path` text NOT NULL,
  `site_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKj2jx0gqa4h7wg8ls0k3y221h2` (`site_id`),
  KEY `path_index` (`path`(250)),
  CONSTRAINT `FKj2jx0gqa4h7wg8ls0k3y221h2` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lemma` (
  `id` int NOT NULL AUTO_INCREMENT,
  `frequency` int NOT NULL,
  `lemma` varchar(255) NOT NULL,
  `site_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfbq251d28jauqlxirb1k2cjag` (`site_id`),
  CONSTRAINT `FKfbq251d28jauqlxirb1k2cjag` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `index` (
  `id` int NOT NULL AUTO_INCREMENT,
  `rank` float NOT NULL,
  `lemma_id` int DEFAULT NULL,
  `page_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2c20dhbpq33mnb1awur1tpwd2` (`lemma_id`),
  KEY `FKsq3363uoow6fmurlfheackwgc` (`page_id`),
  CONSTRAINT `FK2c20dhbpq33mnb1awur1tpwd2` FOREIGN KEY (`lemma_id`) REFERENCES `lemma` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FKsq3363uoow6fmurlfheackwgc` FOREIGN KEY (`page_id`) REFERENCES `page` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;