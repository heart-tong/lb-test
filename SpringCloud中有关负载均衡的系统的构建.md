## SpringCloud中有关负载均衡的系统的构建

[TOC]

源码：https://github.com/heart-tong/lb-test



### 1. 准备工作

要学习负载均衡肯定需要一个需要负载均衡的环境，所以我们先构建一个REST微服务工程，包括以下模块：

1. 整体父工程`lbtest`

   1. 新建Maven父工程，packageing为pom模式。该工程主要是定义POM文件，将后续各个子模块公用的jar包等统一提出来，类似一个抽象父类。

      pom.xml文件如下：

      ~~~properties
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
      
          <groupId>com.lovel.heart</groupId>
          <artifactId>lb-test</artifactId>
          <version>1.0-SNAPSHOT</version>
          <packaging>pom</packaging>
      
          <properties>
              <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              <maven.compiler.source>1.8</maven.compiler.source>
              <maven.compiler.target>1.8</maven.compiler.target>
              <junit.version>4.12</junit.version>
              <log4j.version>1.2.17</log4j.version>
              <lombok.version>1.16.18</lombok.version>
          </properties>
      
          <dependencyManagement>
              <dependencies>
                  <dependency>
                      <groupId>org.springframework.cloud</groupId>
                      <artifactId>spring-cloud-dependencies</artifactId>
                      <version>Dalston.SR1</version>
                      <type>pom</type>
                      <scope>import</scope>
                  </dependency>
      
                  <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-dependencies</artifactId>
                      <version>1.5.9.RELEASE</version>
                      <type>pom</type>
                      <scope>import</scope>
                  </dependency>
      
                  <dependency>
                      <groupId>mysql</groupId>
                      <artifactId>mysql-connector-java</artifactId>
                      <version>5.0.4</version>
                  </dependency>
      
                  <dependency>
                      <groupId>com.alibaba</groupId>
                      <artifactId>druid</artifactId>
                      <version>1.0.31</version>
                  </dependency>
      
                  <dependency>
                      <groupId>org.mybatis.spring.boot</groupId>
                      <artifactId>mybatis-spring-boot-starter</artifactId>
                      <version>1.3.0</version>
                  </dependency>
      
                  <dependency>
                      <groupId>ch.qos.logback</groupId>
                      <artifactId>logback-core</artifactId>
                      <version>1.2.3</version>
                  </dependency>
      
                  <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>${junit.version}</version>
                      <scope>test</scope>
                  </dependency>
      
                  <dependency>
                      <groupId>log4j</groupId>
                      <artifactId>log4j</artifactId>
                      <version>${log4j.version}</version>
                  </dependency>
              </dependencies>
          </dependencyManagement>
      </project>
      ~~~

2. 公共子模块`test-api`

   1. 新建子模块，在父工程上右键选择Module即可，待子模块创建成功在父工程pom.xml文件会出现

      ~~~properties
      <modules>
          <module>test-api</module>
      </modules>
      ~~~

   2. 修改pom.xml文件

      ~~~properties
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>lb-test</artifactId>
              <groupId>com.lovel.heart</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <modelVersion>4.0.0</modelVersion>
      
          <artifactId>test-api</artifactId>
      
          <dependencies>
              <dependency>
                  <groupId>org.projectlombok</groupId>
                  <artifactId>lombok</artifactId>
              </dependency>
          </dependencies>
      
      </project>
      ~~~

   3. 新建部门Entity

      ~~~java
      package com.lovel.heart.entities;
      
      import lombok.Data;
      import lombok.NoArgsConstructor;
      import lombok.experimental.Accessors;
      import java.io.Serializable;
      
      @SuppressWarnings("serial")
      @NoArgsConstructor
      @Data
      @Accessors(chain=true)
      public class Dept implements Serializable {
      
          private Long  deptno;
          private String  dname;
          private String  db_source;
      
          public Dept(String dname) {
              super();
              this.dname = dname;
          }
      }
      ~~~

   4. `mvn clean install`后给其它模块引用，达到通用目的。其他模块需要使用部门实体，直接引用本模块即可，不用每个工程都定义一份。

3. 服务提供模块`provider-dept-8001`

   1. 创建子模块，父工程pom.xml也会随着发生变化

   2. 修改pom.xml文件

      ~~~properties
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>lb-test</artifactId>
              <groupId>com.lovel.heart</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <modelVersion>4.0.0</modelVersion>
      
          <artifactId>provider-dept-8001</artifactId>
      
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>mysql</groupId>
                  <artifactId>mysql-connector-java</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>com.alibaba</groupId>
                  <artifactId>druid</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>ch.qos.logback</groupId>
                  <artifactId>logback-core</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.mybatis.spring.boot</groupId>
                  <artifactId>mybatis-spring-boot-starter</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-jetty</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-test</artifactId>
              </dependency>
      
              <!-- 修改后立即生效，热部署 -->
              <dependency>
                  <groupId>org.springframework</groupId>
                  <artifactId>springloaded</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-devtools</artifactId>
              </dependency>
      
              <!-- 引用自定义公共子模块 -->
              <dependency>
                  <groupId>com.lovel.heart</groupId>
                  <artifactId>test-api</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <scope>compile</scope>
              </dependency>
          </dependencies>
      </project>
      ~~~

   3. 添加application.yml

      ~~~yaml
      server:
        port: 8001
      
      mybatis:
        config-location: classpath:mybatis/mybatis.cfg.xml        # mybatis配置文件所在路径
        type-aliases-package: com.atguigu.springcloud.entities    # 所有Entity别名类所在包
        mapper-locations:
        - classpath:mybatis/mapper/**/*.xml                       # mapper映射文件
      
      spring:
         application:
          name: microservicecloud-dept 
         datasource:
          type: com.alibaba.druid.pool.DruidDataSource            # 当前数据源操作类型
          driver-class-name: org.gjt.mm.mysql.Driver              # mysql驱动包
          url: jdbc:mysql://localhost:3306/cloudDB01              # 数据库名称
          username: root
          password: 1234
          dbcp2:
            min-idle: 5                                           # 数据库连接池的最小维持连接数
            initial-size: 5                                       # 初始化连接数
            max-total: 5                                          # 最大连接数
            max-wait-millis: 200                                  # 等待连接获取的最大超时时间
      ~~~

   4. 工程src/main/resources目录下新建mybatis文件夹后新建mybatis.cfg.xml文件

      ~~~xml
      <?xml version="1.0" encoding="UTF-8" ?>
      <!DOCTYPE configuration
              PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
              "http://mybatis.org/dtd/mybatis-3-config.dtd">
      
      <configuration>
          <settings>
              <setting name="cacheEnabled" value="true"/><!-- 二级缓存开启 -->
          </settings>
      </configuration>
      ~~~

   5. 在数据库创建对应的库并插入数据

      ~~~sql
      DROP DATABASE IF EXISTS cloudDB01;
      CREATE DATABASE cloudDB01 CHARACTER SET UTF8;
      USE cloudDB01;
      CREATE TABLE dept
      (
        deptno BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
        dname VARCHAR(60),
        db_source   VARCHAR(60)
      );
      
      INSERT INTO dept(dname,db_source) VALUES('开发部',DATABASE());
      INSERT INTO dept(dname,db_source) VALUES('人事部',DATABASE());
      INSERT INTO dept(dname,db_source) VALUES('财务部',DATABASE());
      INSERT INTO dept(dname,db_source) VALUES('市场部',DATABASE());
      INSERT INTO dept(dname,db_source) VALUES('运维部',DATABASE());
      
      SELECT * FROM dept; 
      ~~~

   6. DeptDao部门接口

      ~~~java
      package com.lovel.heart.dao;
      
      import com.lovel.heart.entities.Dept;
      import org.apache.ibatis.annotations.Mapper;
      
      import java.util.List;
      
      @Mapper
      public interface DeptDao {
          public boolean addDept(Dept dept);
      
          public Dept findById(Long id);
      
          public List<Dept> findAll();
      }
      
      ~~~

   7. 工程src/main/resources/mybatis目录下新建mapper文件夹后再建DeptMapper.xml

      ~~~xml
      <?xml version="1.0" encoding="UTF-8" ?>
      <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
              "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
      
      <mapper namespace="com.lovel.heart.dao.DeptDao">
          
          <select id="findById" resultType="Dept" parameterType="Long">
         select deptno,dname,db_source from dept where deptno=#{deptno};
        </select>
      
          <select id="findAll" resultType="Dept">
         select deptno,dname,db_source from dept;
        </select>
      
          <insert id="addDept" parameterType="Dept">
         INSERT INTO dept(dname,db_source) VALUES(#{dname},DATABASE());
        </insert>
      
      </mapper>
      ~~~

   8. DeptService部门服务接口

      ~~~java
      package com.lovel.heart.service;
      
      import com.lovel.heart.entities.Dept;
      
      import java.util.List;
      
      public interface DeptService {
      
          public boolean add(Dept dept);
      
          public Dept get(Long id);
      
          public List<Dept> list();
      
      }
      
      ~~~

   9. DeptServiceImpl部门服务接口实现类

      ~~~java
      package com.lovel.heart.service.impl;
      
      import com.lovel.heart.dao.DeptDao;
      import com.lovel.heart.entities.Dept;
      import com.lovel.heart.service.DeptService;
      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.stereotype.Service;
      
      import java.util.List;
      
      @Service
      public class DeptServiceImpl implements DeptService {
      
          @Autowired
          private final DeptDao dao;
      
          @Override
          public boolean add(Dept dept) {
              return dao.addDept(dept);
          }
      
          @Override
          public Dept get(Long id) {
              return dao.findById(id);
          }
      
          @Override
          public List<Dept> list() {
              return dao.findAll();
          }
      }
      ~~~

   10. DeptController部门微服务提供者REST

       ~~~java
       package com.lovel.heart.controller;
       
       import com.lovel.heart.entities.Dept;
       import com.lovel.heart.service.DeptService;
       import org.springframework.beans.factory.annotation.Autowired;
       import org.springframework.web.bind.annotation.*;
       
       import java.util.List;
       
       @RestController
       public class DeptController {
       
           @Autowired
           private DeptService service;
       
           @RequestMapping(value = "/dept/add", method = RequestMethod.POST)
           public boolean add(@RequestBody Dept dept) {
               return service.add(dept);
           }
       
       
           @RequestMapping(value = "/dept/get/{id}", method = RequestMethod.GET)
           public Dept get(@PathVariable("id") Long id) {
               return service.get(id);
           }
       
       
           @RequestMapping(value = "/dept/list", method = RequestMethod.GET)
           public List<Dept> list() {
               return service.list();
           }
       }
       ~~~

   11. 主启动类DeptProvider8001App

       ~~~java
       package com.lovel.heart;
       
       import org.springframework.boot.SpringApplication;
       import org.springframework.boot.autoconfigure.SpringBootApplication;
       import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
       
       @SpringBootApplication
       public class DeptProvider8001App {
       
           public static void main(String[] args) {
               SpringApplication.run(DeptProvider8001App.class, args);
           }
       }
       ~~~

   12. 测试

       ~~~http
       http://localhost:8001/dept/get/2
       http://localhost:8001/dept/list
       ~~~

   13. 最终项目如下：

       ![mark](http://piiw75qxc.bkt.clouddn.com/blog/20181219/fU4KrqYB3Bsi.png?imagesli)

4. 服务消费模块`consumer-dept-80`

   1. 新建项目，修改pom.xml文件

      ~~~properties
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>lb-test</artifactId>
              <groupId>com.lovel.heart</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <modelVersion>4.0.0</modelVersion>
      
          <artifactId>consumer-dept-80</artifactId>
      
          <description>部门微服务消费者</description>
          <dependencies>
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
              </dependency>
      
              <!-- 修改后立即生效，热部署 -->
              <dependency>
                  <groupId>org.springframework</groupId>
                  <artifactId>springloaded</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-devtools</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>com.lovel.heart</groupId>
                  <artifactId>test-api</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <scope>compile</scope>
              </dependency>
          </dependencies>
      </project>
      ~~~

   2. 添加application.yml

      ~~~yaml
      server:
        port: 80
      ~~~

   3. com.lovel.heart.cfgbeans包下ConfigBean的编写（类似spring里面的applicationContext.xml写入的注入Bean）

      ~~~java
      package com.lovel.heart.cfgbeans;
      
      import org.springframework.cloud.client.loadbalancer.LoadBalanced;
      import org.springframework.context.annotation.Bean;
      import org.springframework.context.annotation.Configuration;
      import org.springframework.web.client.RestTemplate;
      
      @Configuration
      
      public class ConfigBean {
      
          @Bean
          public RestTemplate getRestTemplate() {
              return new RestTemplate();
          }
      }
      ~~~

   4. com.lovel.heart.controller包下新建DeptControllerConsumer部门微服务消费者REST

      ~~~java
      package com.lovel.heart.controller;
      
      import com.lovel.heart.entities.Dept;
      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.web.bind.annotation.PathVariable;
      import org.springframework.web.bind.annotation.RequestMapping;
      import org.springframework.web.bind.annotation.RestController;
      import org.springframework.web.client.RestTemplate;
      
      import java.util.List;
      
      @RestController
      public class DeptControllerConsumer {
          private static final String REST_URL_PREFIX = "http://localhost:8001";
      
          @Autowired
          private RestTemplate restTemplate;
      
          @RequestMapping(value = "/consumer/dept/add")
          public boolean add(Dept dept) {
              return restTemplate.postForObject(REST_URL_PREFIX + "/dept/add", dept, Boolean.class);
          }
      
          @RequestMapping(value = "/consumer/dept/get/{id}")
          public Dept get(@PathVariable("id") Long id) {
              return restTemplate.getForObject(REST_URL_PREFIX + "/dept/get/" + id, Dept.class);
          }
      
          @SuppressWarnings("unchecked")
          @RequestMapping(value = "/consumer/dept/list")
          public List<Dept> list() {
              return restTemplate.getForObject(REST_URL_PREFIX + "/dept/list", List.class);
          }
      }
      ~~~

      >RestTemplate提供了多种便捷访问远程Http服务的方法， 
      >
      >是一种简单便捷的访问restful服务模板类，是Spring提供的用于访问Rest服务的客户端模板工具集

   5. Deptconsumer80App主启动类

      ~~~java
      package com.lovel.heart;
      
      import org.springframework.boot.SpringApplication;
      import org.springframework.boot.autoconfigure.SpringBootApplication;
      import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
      
      @SpringBootApplication
      public class DeptConsumer80App {
      
          public static void main(String[] args) {
              SpringApplication.run(DeptConsumer80App.class, args);
          }
      }
      
      ~~~

   6. 测试

      ~~~http
      http://localhost/consumer/dept/get/2
      http://localhost/consumer/dept/list
      http://localhost/consumer/dept/add?dname=AI
      ~~~



### 2. 使用Eureka做服务注册与发现

#### 2.1 Eureka是什么

Eureka是Netflix的一个子模块，也是核心模块之一。Eureka是一个基于REST的服务，用于定位服务，以实现云端中间层服务发现和故障转移。

服务注册与发现对于微服务架构来说是非常重要的，有了服务发现与注册，只需要使用服务的标识符，就可以访问到服务，而不需要修改服务调用的配置文件了。功能类似于dubbo的注册中心，比如Zookeeper。

#### 2.2 三大角色

1. Eureka Server 提供服务注册和发现
2. Service Provider服务提供方将自身服务注册到Eureka，从而使服务消费方能够找到
3. Service Consumer服务消费方从Eureka获取注册服务列表，从而能够消费服务

#### 2.3 构建

1. 新建子项目`eureka-7001`

   1. 创建子项目，修改pom.xml

      ~~~properties
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>lb-test</artifactId>
              <groupId>com.lovel.heart</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
      
          <modelVersion>4.0.0</modelVersion>
          <artifactId>eureka-7001</artifactId>
      
          <dependencies>
              <!--eureka-server服务端 -->
              <dependency>
                  <groupId>org.springframework.cloud</groupId>
                  <artifactId>spring-cloud-starter-eureka-server</artifactId>
              </dependency>
      
              <!-- 修改后立即生效，热部署 -->
              <dependency>
                  <groupId>org.springframework</groupId>
                  <artifactId>springloaded</artifactId>
              </dependency>
      
              <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-devtools</artifactId>
              </dependency>
          </dependencies>
      </project>
      ~~~

   2. 添加application.yml

      ~~~yaml
      server:
        port: 7001
      
      eureka:
        instance:
          hostname: localhost #eureka服务端的实例名称
        client:
          register-with-eureka: false #false表示不向注册中心注册自己。
          fetch-registry: false #false表示自己端就是注册中心，我的职责就是维护服务实例，并不需要去检索服务
          service-url:
            defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
      ~~~

   3. EurekaServer7001App主启动类

      ~~~java
      package com.lovel.heart;
      
      import org.springframework.boot.SpringApplication;
      import org.springframework.boot.autoconfigure.SpringBootApplication;
      import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
      
      @SpringBootApplication
      @EnableEurekaServer
      public class EurekaServer7001App {
          public static void main(String[] args) {
              SpringApplication.run(EurekaServer7001App.class, args);
          }
      }
      ~~~

   4. 测试

      ~~~http
      http://localhost:7001/
      ~~~

      会显示eureka的页面，不过没有注册的服务

2. 修改服务提供者`provider-dept-8001`,将其注入erueka注册中心

   1. pom.xml中添加如下部分

      ~~~properties
         <!-- 将微服务provider侧注册进eureka -->
         <dependency>
           <groupId>org.springframework.cloud</groupId>
           <artifactId>spring-cloud-starter-eureka</artifactId>
         </dependency>
      
         <dependency>
           <groupId>org.springframework.cloud</groupId>
           <artifactId>spring-cloud-starter-config</artifactId>
         </dependency>
      ~~~

   2. application.yml添加如下部分

      ~~~yaml
      eureka:
        client: #客户端注册进eureka服务列表内
          service-url: 
            defaultZone: http://localhost:7001/eureka 
      ~~~

   3. 主启动类添加如下注解

      ~~~java
      @EnableEurekaClient
      ~~~

   4. 测试，先要启动EurekaServer，然后启动该模块

      ~~~http
      http://localhost:7001/
      ~~~

      本服务注册到eureka中

   另：Eureka注册微服务信息完善

   1. 主机名称:服务名称修改

      修改provider-dept-8001的application.yml

      ~~~yaml
       eureka:
        client: #客户端注册进eureka服务列表内
          service-url: 
            defaultZone: http://localhost:7001/eureka
        instance:
          instance-id: dept8001
      ~~~

   2. 访问信息有IP信息提示

      修改provider-dept-8001的application.yml，添加第三行内容

      ~~~yaml
        instance:
          instance-id: dept8001
      	prefer-ip-address: true     #访问路径可以显示IP地址
      ~~~

   3. 微服务info内容详细信息

      1. 修改provider-dept-8001的pom.xml，添加如下依赖

         ~~~properties
         <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-actuator</artifactId>
            </dependency>
         ~~~

      2. 总的父工程lbtest修改pom.xml添加构建build信息

         ~~~properties
         <build>
            <finalName>microservicecloud</finalName>
            <resources>
              <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
              </resource>
            </resources>
         
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <configuration>
                  <delimiters>
                   <delimit>$</delimit>
                  </delimiters>
                </configuration>
              </plugin>
            </plugins>
           </build>
         ~~~

      3. 修改provider-dept-8001的application.yml，添加如下内容

         ~~~yaml
         info:
           app.name: atguigu-microservicecloud
           company.name: www.atguigu.com
           build.artifactId: $project.artifactId$
           build.version: $project.version$
         ~~~

      #### 4. 集群配置

      1. 新建两个子模块`eureka-7002`、`eureka-7003`

      2. 按照7001为模板粘贴POM

      3. 修改7002和7003的主启动类

      4. 修改映射配置

         1. 找到C:\Windows\System32\drivers\etc路径下的hosts文件，这是windows的配置方法，Mac\linux请自行百度

         2. 修改映射配置添加进hosts文件

            ~~~xml
            127.0.0.1  eureka7001.com
            127.0.0.1  eureka7002.com
            127.0.0.1  eureka7003.com
            ~~~

         3. 3台eureka服务器的application.yml配置

            7001：

            ~~~yaml
            server:
              port: 7001
            
            eureka:
              instance:
                hostname: eureka7001.com #eureka服务端的实例名称
              client:
                register-with-eureka: false #false表示不向注册中心注册自己。
                fetch-registry: false #false表示自己端就是注册中心，我的职责就是维护服务实例，并不需要去检索服务
                service-url:
                  defaultZone: http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/
            ~~~

            7002:

            ~~~yaml
            server:
              port: 7002
            
            eureka:
              instance:
                hostname: eureka7002.com #eureka服务端的实例名称
              client:
                register-with-eureka: false #false表示不向注册中心注册自己。
                fetch-registry: false #false表示自己端就是注册中心，我的职责就是维护服务实例，并不需要去检索服务
                service-url:
                  defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7003.com:7003/eureka/
            ~~~

            7003:

            ~~~yaml
            server:
              port: 7003
            
            eureka:
              instance:
                hostname: eureka7003.com #eureka服务端的实例名称
              client:
                register-with-eureka: false #false表示不向注册中心注册自己。
                fetch-registry: false #false表示自己端就是注册中心，我的职责就是维护服务实例，并不需要去检索服务
                service-url:
                  defaultZone: http://eureka7002.com:7002/eureka/,http://eureka7001.com:7001/eureka/
            ~~~

         4. 修改provider-dept-8001的application.yml将其发布到上面3台eureka集群配置中

            ~~~yaml
            server:
              port: 8001
              
            mybatis:
              config-location: classpath:mybatis/mybatis.cfg.xml        # mybatis配置文件所在路径
              type-aliases-package: com.lovel.heart.entities    # 所有Entity别名类所在包
              mapper-locations:
              - classpath:mybatis/mapper/**/*.xml                       # mapper映射文件
            
            spring:
              application:
                name: lb-test-dept
              datasource:
                type: com.alibaba.druid.pool.DruidDataSource            # 当前数据源操作类型
                driver-class-name: org.gjt.mm.mysql.Driver              # mysql驱动包
                url: jdbc:mysql://localhost:3306/cloudDB01              # 数据库名称
                username: root
                password: 1234
                dbcp2:
                  min-idle: 5                                           # 数据库连接池的最小维持连接数
                  initial-size: 5                                       # 初始化连接数
                  max-total: 5                                          # 最大连接数
                  max-wait-millis: 200                                  # 等待连接获取的最大超时时间
            
            eureka:
              client: #客户端注册进eureka服务列表内
                service-url:
                  defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/
            
              instance:
                instance-id: dept8001
                prefer-ip-address: true
            
            info:
              app.name: heart-lbtest
              company.name: heart.lovel.com
              build.artifactId: $project.artifactId$
              build.version: $project.version$
            ~~~

### 3. 负载均衡

LB，即负载均衡(Load Balance)，在微服务或分布式集群中经常用的一种应用。

负载均衡简单的说就是将用户的请求平摊的分配到多个服务上，从而达到系统的HA。

常见的负载均衡有软件Nginx，LVS，硬件 F5等。

相应的在中间件，例如：dubbo和SpringCloud中均给我们提供了负载均衡。 

现在对负载均衡还有另一种区分方式：

1. 集中式LB

即在服务的消费方和提供方之间使用独立的LB设施(可以是硬件，如F5, 也可以是软件，如nginx), 由该设施负责把访问请求通过某种策略转发至服务的提供方；

2. 进程内LB

将LB逻辑集成到消费方，消费方从服务注册中心获知有哪些地址可用，然后自己再从这些地址中选择出一个合适的服务器。

Ribbon就属于进程内LB，它只是一个类库，集成于消费方进程，消费方通过它来获取到服务提供方的地址。

#### 3.1 Ribbon

Spring Cloud Ribbon是基于Netflix Ribbon实现的一套 **客户端**  **负载均衡** 的工具。

主要功能是提供客户端的软件负载均衡算法，将Netflix的中间层服务连接在一起。Ribbon客户端组件提供一系列完善的配置项如连接超时，重试等。简单的说，就是在配置文件中列出Load Balancer（简称LB）后面所有的机器，或者在注册中心获取所有服务的提供者，Ribbon会自动的帮助你基于某种规则（如简单轮询，随机连接等）去连接这些机器。我们也很容易使用Ribbon实现自定义的负载均衡算法。

我们构建一个能够实现Ribbon负载均衡的消费者

##### 3.1.1 初步配置具体步骤如下：

1. 修改consumer-dept-80的pom.xml文件，引入Ribbon的依赖

   ~~~properties
   <!-- Ribbon相关 -->
       <dependency>
         <groupId>org.springframework.cloud</groupId>
         <artifactId>spring-cloud-starter-eureka</artifactId>
       </dependency>
    
       <dependency>
         <groupId>org.springframework.cloud</groupId>
         <artifactId>spring-cloud-starter-ribbon</artifactId>
       </dependency>
    
       <dependency>
         <groupId>org.springframework.cloud</groupId>
         <artifactId>spring-cloud-starter-config</artifactId>
       </dependency>
   ~~~

2. 修改application.yml   追加eureka的服务注册地址

   ~~~yaml
   eureka:
     client:
       register-with-eureka: false
       service-url: 
         defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/
   ~~~

3. 对ConfigBean进行新注解@LoadBalanced    获得Rest时加入Ribbon的配置

   ~~~java
   package com.lovel.heart.cfgbeans;
   
   import org.springframework.cloud.client.loadbalancer.LoadBalanced;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.web.client.RestTemplate;
   
   @Configuration
   public class ConfigBean {
   
       @Bean
       @LoadBalanced
       public RestTemplate getRestTemplate() {
           return new RestTemplate();
       }
   }
   ~~~

4. 主启动类DeptConsumer80App添加@EnableEurekaClient

   ~~~java
   package com.lovel.heart;
   
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
   
   @SpringBootApplication
   @EnableEurekaClient
   public class DeptConsumer80App {
   
       public static void main(String[] args) {
           SpringApplication.run(DeptConsumer80App.class, args);
       }
   }
   ~~~

   相较于之前只添加了第8行的@EnableEurekaClient

5. 修改DeptControllerConsumer客户端访问类

   将原本的

   ~~~java
   private static final String REST_URL_PREFIX = "http://localhost:8001";
   ~~~

   置换为

   ~~~java
   private static final String REST_URL_PREFIX = "http://LB-TEST-DEPT";
   ~~~

6. 先启动3个eureka集群后，再启动provider-dept-8001并注册进eureka，启动consumer-dept-80进行测试

   ~~~http
   http://localhost/consumer/dept/get/1
   http://localhost/consumer/dept/list
   http://localhost/consumer/dept/add?dname=大数据部
   ~~~

   经过以上步骤的初步配置之后，Ribbon和Eureka整合后Consumer可以直接调用服务而不用再关心地址和端口号

##### 3.1.2 Ribbon负载均衡

  **工作步骤：**

  Ribbon在工作时分成两步

  第一步先选择 EurekaServer ,它优先选择在同一个区域内负载较少的server.

  第二步再根据用户指定的策略，在从server取到的服务注册列表中选择一个地址。

  其中Ribbon提供了多种策略：比如轮询、随机和根据响应时间加权



因为做负载均衡需要有多个提供同样服务的模块，所以我们先做一个提前准备：

1. 参考provider-dept-8001，新建两份，分别命名为8002，8003

2. 新建8002/8003数据库，各自微服务分别连各自的数据库，数据库sql语句如下

   8002：

   ~~~sql
   DROP DATABASE IF EXISTS cloudDB02;
   CREATE DATABASE cloudDB02 CHARACTER SET UTF8;
   USE cloudDB02;
   
   CREATE TABLE dept
   (
     deptno BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
     dname VARCHAR(60),
     db_source   VARCHAR(60)
   );
   
   INSERT INTO dept(dname,db_source) VALUES('开发部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('人事部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('财务部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('市场部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('运维部',DATABASE()); 
   
   SELECT * FROM dept; 
   ~~~

   8003：

   ~~~sql
   DROP DATABASE IF EXISTS cloudDB03;
   CREATE DATABASE cloudDB03 CHARACTER SET UTF8;
   USE cloudDB03;
   
   CREATE TABLE dept
   (
     deptno BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
     dname VARCHAR(60),
     db_source   VARCHAR(60)
   );
   
   INSERT INTO dept(dname,db_source) VALUES('开发部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('人事部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('财务部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('市场部',DATABASE());
   INSERT INTO dept(dname,db_source) VALUES('运维部',DATABASE());
   
   SELECT * FROM dept;
   ~~~

3. 修改8002/8003各自application.yml

   8002：

   ~~~yaml
   server:
     port: 8002
     
   mybatis:
     config-location: classpath:mybatis/mybatis.cfg.xml        # mybatis配置文件所在路径
     type-aliases-package: com.lovel.heart.entities    # 所有Entity别名类所在包
     mapper-locations:
     - classpath:mybatis/mapper/**/*.xml                       # mapper映射文件
   
   spring:
     application:
       name: lb-test-dept
     datasource:
       type: com.alibaba.druid.pool.DruidDataSource            # 当前数据源操作类型
       driver-class-name: org.gjt.mm.mysql.Driver              # mysql驱动包
       url: jdbc:mysql://localhost:3306/cloudDB02              # 数据库名称
       username: root
       password: 1234
       dbcp2:
         min-idle: 5                                           # 数据库连接池的最小维持连接数
         initial-size: 5                                       # 初始化连接数
         max-total: 5                                          # 最大连接数
         max-wait-millis: 200                                  # 等待连接获取的最大超时时间
         
   eureka:
     client: #客户端注册进eureka服务列表内
       service-url:
         defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/
     instance:
       instance-id: dept8002
       prefer-ip-address: true
   
   info:
     app.name: heart-lbtest
     company.name: heart.lovel.com
     build.artifactId: $project.artifactId$
     build.version: $project.version$
   ~~~

   8003:

   ~~~yaml
   server:
     port: 8003
     
   mybatis:
     config-location: classpath:mybatis/mybatis.cfg.xml        # mybatis配置文件所在路径
     type-aliases-package: com.lovel.heart.entities    # 所有Entity别名类所在包
     mapper-locations:
     - classpath:mybatis/mapper/**/*.xml                       # mapper映射文件
   
   spring:
     application:
       name: lb-test-dept
     datasource:
       type: com.alibaba.druid.pool.DruidDataSource            # 当前数据源操作类型
       driver-class-name: org.gjt.mm.mysql.Driver              # mysql驱动包
       url: jdbc:mysql://localhost:3306/cloudDB03              # 数据库名称
       username: root
       password: 1234
       dbcp2:
         min-idle: 5                                           # 数据库连接池的最小维持连接数
         initial-size: 5                                       # 初始化连接数
         max-total: 5                                          # 最大连接数
         max-wait-millis: 200                                  # 等待连接获取的最大超时时间
   
   eureka:
     client: #客户端注册进eureka服务列表内
       service-url:
         defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/
     instance:
       instance-id: dept8003
       prefer-ip-address: true
   
   info:
     app.name: heart-lbtest
     company.name: heart.lovel.com
     build.artifactId: $project.artifactId$
     build.version: $project.version$
   ~~~

   这里要保证三个服务的

   ~~~yaml
   spring:
     application:
       name: lb-test-dept
   ~~~

   相同，端口和数据库各用各自的

   ~~~yaml
   server:
     port: 8003
     
   spring:
     datasource:             # mysql驱动包
       url: jdbc:mysql://localhost:3306/cloudDB03   
   ~~~

4. 启动3个eureka集群配置区，启动3个Dept微服务启动并各自测试

   ~~~http
   http://localhost:8001/dept/list
   http://localhost:8002/dept/list
   http://localhost:8003/dept/list
   ~~~

5. 启动consumer-dept-80,客户端通过Ribbo完成负载均衡并访问上一步的Dept微服务

   ~~~http
   http://localhost/consumer/dept/list
   ~~~

   注意观察看到返回的数据库名字，各不相同，负载均衡实现

**总结**：Ribbon其实就是一个软负载均衡的客户端组件，他可以和其他所需请求的客户端结合使用，和eureka结合只是其中的一个实例。

#### 3.2 Ribbon分析

首先我们仔细回想一下，使用Ribbon进行负载均衡，除了在消费者的pom.xml中导入Ribbon应用的依赖，就只在`RestTemplate上`添加了一个`@LoadBalanced`的注解

那么我们分析Ribbon就从这个注解开始

从`@LoadBalanced`注解源码的注释中，我们可以知道该注解用来给`RestTemplate`标记，以使用负载均衡的客户端（`LoadBalancerClient`）来配置它。

那我们就再来看一下`LoadBalancerClient`

~~~java
public interface LoadBalancerClient extends ServiceInstanceChooser {
    
    <T> T execute(String var1, LoadBalancerRequest<T> var2) throws IOException;

    <T> T execute(String var1, ServiceInstance var2, LoadBalancerRequest<T> var3) throws IOException;

    URI reconstructURI(ServiceInstance var1, URI var2);
}
~~~

它扩展自`ServiceInstanceChooser`接口

~~~java
public interface ServiceInstanceChooser {
    ServiceInstance choose(String var1);
}
~~~

从该接口中，我们可以通过定义的抽象方法来了解到客户端负载均衡器中应具备的几种能力：

- `ServiceInstance choose(String serviceId)`：根据传入的服务名`serviceId`，从负载均衡器中挑选一个对应服务的实例。
- `T execute(String serviceId, LoadBalancerRequest request) throws IOException`：使用从负载均衡器中挑选出的服务实例来执行请求内容。
- `URI reconstructURI(ServiceInstance instance, URI original)`：为系统构建一个合适的“host:port”形式的URI。在分布式系统中，我们使用逻辑上的服务名称作为host来构建URI（替代服务实例的“host:port”形式）进行请求，比如：`http://myservice/path/to/service`。在该操作的定义中，前者`ServiceInstance`对象是带有host和port的具体服务实例，而后者URI对象则是使用逻辑服务名定义为host的URI，而返回的URI内容则是通过`ServiceInstance`的服务实例详情拼接出的具体“host:post”形式的请求地址。

顺着`LoadBalancerClient`接口的所属包`org.springframework.cloud.client.loadbalancer`，我们对其内容进行整理，可以得出如下图的关系：

![mark](http://piiw75qxc.bkt.clouddn.com/blog/20181220/g7BRSSdIKx0c.png?imageslim)

从类的命名上我们初步判断`LoadBalancerAutoConfiguration`为实现客户端负载均衡器的自动化配置类。

我们来看一下这个配置类的内容

~~~java
@Configuration
@ConditionalOnClass({RestTemplate.class})
@ConditionalOnBean({LoadBalancerClient.class})
@EnableConfigurationProperties({LoadBalancerRetryProperties.class})
public class LoadBalancerAutoConfiguration {
  
    @Bean
    @ConditionalOnMissingBean
    public LoadBalancerRequestFactory loadBalancerRequestFactory(LoadBalancerClient loadBalancerClient) {
        return new LoadBalancerRequestFactory(loadBalancerClient, this.transformers);
    }

   @Configuration
    @ConditionalOnMissingClass({"org.springframework.retry.support.RetryTemplate"})
    static class LoadBalancerInterceptorConfig {
        LoadBalancerInterceptorConfig() {
        }

        @Bean
        public LoadBalancerInterceptor ribbonInterceptor(LoadBalancerClient loadBalancerClient, LoadBalancerRequestFactory requestFactory) {
            return new LoadBalancerInterceptor(loadBalancerClient, requestFactory);
        }

        @Bean
        @ConditionalOnMissingBean
        public RestTemplateCustomizer restTemplateCustomizer(final LoadBalancerInterceptor loadBalancerInterceptor) {
            return new RestTemplateCustomizer() {
                public void customize(RestTemplate restTemplate) {
                    List<ClientHttpRequestInterceptor> list = new ArrayList(restTemplate.getInterceptors());
                    list.add(loadBalancerInterceptor);
                    restTemplate.setInterceptors(list);
                }
            };
        }
    }
}
~~~

从`LoadBalancerAutoConfiguration`类头上的注解可以知道Ribbon实现的负载均衡自动化配置需要满足下面两个条件：

- `@ConditionalOnClass(RestTemplate.class)`：`RestTemplate`类必须存在于当前工程的环境中。
- `@ConditionalOnBean(LoadBalancerClient.class)`：在Spring的Bean工程中有必须有`LoadBalancerClient`的实现Bean。

在该自动化配置类中，主要做了下面三件事：

- 创建了一个`LoadBalancerInterceptor`的Bean，用于实现对客户端发起请求时进行拦截，以实现客户端负载均衡。
- 创建了一个`RestTemplateCustomizer`的Bean，用于给`RestTemplate`增加`LoadBalancerInterceptor`拦截器。
- 维护了一个被`@LoadBalanced`注解修饰的`RestTemplate`对象列表，并在这里进行初始化，通过调用`RestTemplateCustomizer`的实例来给需要客户端负载均衡的`RestTemplate`增加`LoadBalancerInterceptor`拦截器。

接下来，我们看看`LoadBalancerInterceptor`拦截器是如何将一个普通的`RestTemplate`变成客户端负载均衡的：

~~~java
public class LoadBalancerInterceptor implements ClientHttpRequestInterceptor {
    private LoadBalancerClient loadBalancer;
    private LoadBalancerRequestFactory requestFactory;

    public LoadBalancerInterceptor(LoadBalancerClient loadBalancer, LoadBalancerRequestFactory requestFactory) {
        this.loadBalancer = loadBalancer;
        this.requestFactory = requestFactory;
    }

    public LoadBalancerInterceptor(LoadBalancerClient loadBalancer) {
        this(loadBalancer, new LoadBalancerRequestFactory(loadBalancer));
    }

    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI originalUri = request.getURI();
        String serviceName = originalUri.getHost();
        Assert.state(serviceName != null, "Request URI does not contain a valid hostname: " + originalUri);
        return (ClientHttpResponse)this.loadBalancer.execute(serviceName, this.requestFactory.createRequest(request, body, execution));
    }
}
~~~

通过源码以及之前的自动化配置类，我们可以看到在拦截器中注入了`LoadBalancerClient`的实现。当一个被`@LoadBalanced`注解修饰的`RestTemplate`对象向外发起HTTP请求时，会被`LoadBalancerInterceptor`类的`intercept`函数所拦截。由于我们在使用RestTemplate时候采用了服务名作为host，所以直接从`HttpRequest`的URI对象中通过getHost()就可以拿到服务名，然后调用`execute`函数去根据服务名来选择实例并发起实际的请求。

分析到这里，`LoadBalancerClient`还只是一个抽象的负载均衡器接口，所以我们还需要找到它的具体实现类来进一步分析。通过查看ribbon的源码，我们可以很容易的在`org.springframework.cloud.netflix.ribbon`包下找到对应的实现类：`RibbonLoadBalancerClient`。

~~~java
public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
        ILoadBalancer loadBalancer = this.getLoadBalancer(serviceId);
        Server server = this.getServer(loadBalancer);
        if (server == null) {
            throw new IllegalStateException("No instances available for " + serviceId);
        } else {
            RibbonLoadBalancerClient.RibbonServer ribbonServer = new RibbonLoadBalancerClient.RibbonServer(serviceId, server, this.isSecure(server, serviceId), this.serverIntrospector(serviceId).getMetadata(server));
            return this.execute(serviceId, ribbonServer, request);
        }
    }

    public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request) throws IOException {
        Server server = null;
        if (serviceInstance instanceof RibbonLoadBalancerClient.RibbonServer) {
            server = ((RibbonLoadBalancerClient.RibbonServer)serviceInstance).getServer();
        }

        if (server == null) {
            throw new IllegalStateException("No instances available for " + serviceId);
        } else {
            RibbonLoadBalancerContext context = this.clientFactory.getLoadBalancerContext(serviceId);
            RibbonStatsRecorder statsRecorder = new RibbonStatsRecorder(context, server);

            try {
                T returnVal = request.apply(serviceInstance);
                statsRecorder.recordStats(returnVal);
                return returnVal;
            } catch (IOException var8) {
                statsRecorder.recordStats(var8);
                throw var8;
            } catch (Exception var9) {
                statsRecorder.recordStats(var9);
                ReflectionUtils.rethrowRuntimeException(var9);
                return null;
            }
        }
    }
~~~

可以看到，在`execute`函数的实现中，第一步做的就是通过`getServer`根据传入的服务名`serviceId`去获得具体的服务实例：

~~~java
protected Server getServer(ILoadBalancer loadBalancer) {
    if (loadBalancer == null) {
        return null;
    }
    return loadBalancer.chooseServer("default");
}
~~~

通过`getServer`函数的实现源码，我们可以看到这里获取具体服务实例的时候并没有使用`LoadBalancerClient`接口中的`choose`函数，而是使用了ribbon自身的`ILoadBalancer`接口中定义的`chooseServer`函数。

查看`ILoadBalancer`的实现类该方法的实现：

~~~java
public Server chooseServer(Object key) {
        if (this.counter == null) {
            this.counter = this.createCounter();
        }

        this.counter.increment();
        if (this.rule == null) {
            return null;
        } else {
            try {
                return this.rule.choose(key);
            } catch (Exception var3) {
                logger.warn("LoadBalancer [{}]:  Error choosing server for key {}", new Object[]{this.name, key, var3});
                return null;
            }
        }
    }
~~~

这里的`rule`其实就是IRule，至此，拦截器和负载均衡策略关联了起来。

#### 3.3 Ribbon负载均衡策略

系统一共为用户提供了7中负载均衡策略：

![mark](http://piiw75qxc.bkt.clouddn.com/blog/20181220/iP1tCl0q3ike.png?imageslim)

| 策略名                    | 命名             | 策略描述                                                     |
| ------------------------- | ---------------- | ------------------------------------------------------------ |
| BestAvailableRule         | 最低并发策略     | 选择一个最小的并发请求的server                               |
| AvailabilityFilteringRule | 可用过滤策略     | 过滤掉那些因为一直连接失败的被标记为circuit tripped的后端server，并过滤掉那些高并发的的后端server（active connections 超过配置的阈值） |
| WeightedResponseTimeRule  | 响应时间加权策略 | 根据相应时间分配一个weight，相应时间越长，weight越小，被选中的可能性越低。 |
| RetryRule                 | 重试策略         | 对选定的负载均衡策略机上重试机制。                           |
| RoundRobinRule            | 轮询策略         | 顺序循环方式轮询选择server                                   |
| RandomRule                | 随机策略         | 随机选择一个server                                           |
| ZoneAvoidanceRule         | 区域权衡策略     | 复合判断server所在区域的性能和server的可用性选择server       |

默认的是`RoundRobinRule`轮询策略，所以我们就看一下它的源码，了解一下时怎么实现的

~~~java
public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            log.warn("no load balancer");
            return null;
        } else {
            Server server = null;
            int count = 0;

            while(true) {
                if (server == null && count++ < 10) {
                    List<Server> reachableServers = lb.getReachableServers();
                    List<Server> allServers = lb.getAllServers();
                    int upCount = reachableServers.size();
                    int serverCount = allServers.size();
                    if (upCount != 0 && serverCount != 0) {
                        int nextServerIndex = this.incrementAndGetModulo(serverCount);
                        server = (Server)allServers.get(nextServerIndex);
                        if (server == null) {
                            Thread.yield();
                        } else {
                            if (server.isAlive() && server.isReadyToServe()) {
                                return server;
                            }

                            server = null;
                        }
                        continue;
                    }

                    log.warn("No up servers available from load balancer: " + lb);
                    return null;
                }

                if (count >= 10) {
                    log.warn("No available alive servers after 10 tries from load balancer: " + lb);
                }

                return server;
            }
        }
    }
~~~

最主要的就是这个choose方法，主要就是获得现在在用的服务号，一共多少个服务，然后按顺序获得下一个服务号，返回下一个服务。

如果我们不想使用默认的策略我们可以怎样去替换掉它呢？Ribbon给我们提供了一个非常方便的方法，你只需要在配置类ConfigBean中添加

~~~java
@Bean
public IRule myRule() {
    return new RandomRule(); //这里写成你想要替换的策略名即可
}
~~~

这个在Ribbon进行配置的时候当发现你注入了IRule，就会使用你设置的策略替换默认的轮询策略。

既然选择策略只要返回一个新的IRule的实现类即可，那么我们完全可以自己实现IRule的接口来实现我们自己的策略。

首先新建一个IRule的实现类，我是直接复制的RandomRule，然后在它上面进行的修改生成我自己的myRandomRule类

~~~java
public class myRoundRobinRule extends AbstractLoadBalancerRule {
    private AtomicInteger nextServerCyclicCounter;
    private static final boolean AVAILABLE_ONLY_SERVERS = true;
    private static final boolean ALL_SERVERS = false;
    private static Logger log = LoggerFactory.getLogger(RoundRobinRule.class);

    public myRoundRobinRule() {
        this.nextServerCyclicCounter = new AtomicInteger(0);
    }

    public myRoundRobinRule(ILoadBalancer lb) {
        this();
        this.setLoadBalancer(lb);
    }

    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            log.warn("no load balancer");
            return null;
        } else {
            Server server = null;
            int count = 0;

            while(true) {
                if (server == null && count++ < 10) {
                    List<Server> reachableServers = lb.getReachableServers();
                    List<Server> allServers = lb.getAllServers();
                    int upCount = reachableServers.size();
                    int serverCount = allServers.size();
                    if (upCount != 0 && serverCount != 0) {
                        int nextServerIndex = this.incrementAndGetModulo(serverCount) + 1; //每次都多往下走一个跨越式
                        server = (Server)allServers.get(nextServerIndex);
                        if (server == null) {
                            Thread.yield();
                        } else {
                            if (server.isAlive() && server.isReadyToServe()) {
                                return server;
                            }

                            server = null;
                        }
                        continue;
                    }

                    log.warn("No up servers available from load balancer: " + lb);
                    return null;
                }

                if (count >= 10) {
                    log.warn("No available alive servers after 10 tries from load balancer: " + lb);
                }

                return server;
            }
        }
    }

    private int incrementAndGetModulo(int modulo) {
        int current;
        int next;
        do {
            current = this.nextServerCyclicCounter.get();
            next = (current + 1) % modulo;
        } while(!this.nextServerCyclicCounter.compareAndSet(current, next));

        return next;
    }

    @Override
    public Server choose(Object key) {
        return this.choose(this.getLoadBalancer(), key);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
    }
}

~~~

这个choose可以尽情的发挥你自己的想象力，只要符合java规范最后返回一个server怎么写都行。

然后在配置类中将myRule进行一下修改即可使用自己定义的负载均衡策略

~~~java
@Bean
public IRule myRule() {
//    return new RandomRule(); //这里写成你想要替换的策略名即可
    return new myRoundRobinRule();
}
~~~



#### 3.4 Feign

**什么是Feign**

Feign是一个声明式WebService客户端。使用Feign能让编写Web Service客户端更加简单, 它的使用方法是定义一个接口，然后在上面添加注解，同时也支持JAX-RS标准的注解。Feign也支持可拔插式的编码器和解码器。Spring Cloud对Feign进行了封装，使其支持了Spring MVC标准注解和HttpMessageConverters。Feign可以与Eureka和Ribbon组合使用以支持负载均衡。 

 Feign是一个声明式的Web服务客户端，使得编写Web服务客户端变得非常容易，

只需要创建一个接口，然后在上面添加注解即可。 

**Feign能干什么**

Feign旨在使编写Java Http客户端变得更容易。

前面在使用Ribbon+RestTemplate时，利用RestTemplate对http请求的封装处理，形成了一套模版化的调用方法。但是在实际开发中，由于对服务依赖的调用可能不止一处，往往一个接口会被多处调用，所以通常都会针对每个微服务自行封装一些客户端类来包装这些依赖服务的调用。所以，Feign在此基础上做了进一步封装，由他来帮助我们定义和实现依赖服务接口的定义。在Feign的实现下，我们只需创建一个接口并使用注解的方式来配置它(以前是Dao接口上面标注Mapper注解,现在是一个微服务接口上面标注一个Feign注解即可)，即可完成对服务提供方的接口绑定，简化了使用Spring cloud Ribbon时，自动封装服务调用客户端的开发量。

Feign集成了Ribbon

利用Ribbon维护了服务列表信息，并且通过轮询实现了客户端的负载均衡。而与Ribbon不同的是，通过feign只需要定义服务绑定接口且以声明式的方法，优雅而简单的实现了服务调用

##### 3.4.1 构建一个Feign服务

1. 参考consumer-dept-80新建consumer-dept-feign-80

2. consumer-dept-feign-80工程pom.xml修改，主要添加对feign的支持

   ~~~properties
      <dependency>
          <groupId>org.springframework.cloud</groupId>
          <artifactId>spring-cloud-starter-feign</artifactId>
      </dependency>
   ~~~

3. 修改test-api工程

   1. 修改pom.xml

      ~~~properties
       <dependency>
           <groupId>org.springframework.cloud</groupId>
           <artifactId>spring-cloud-starter-feign</artifactId>
         </dependency>
      ~~~

   2. 新建DeptClientService接口并新增注解@FeignClient

      ~~~java
      package com.lovel.heart.service;
      
      import com.lovel.heart.entities.Dept;
      import org.springframework.cloud.netflix.feign.FeignClient;
      import org.springframework.web.bind.annotation.PathVariable;
      import org.springframework.web.bind.annotation.RequestMapping;
      import org.springframework.web.bind.annotation.RequestMethod;
      
      import java.util.List;
      
      @FeignClient(value = "LB-TEST-DEPT")
      public interface DeptClientService {
      
          @RequestMapping(value = "/dept/get/{id}",method = RequestMethod.GET)
      
          public Dept get(@PathVariable("id") long id);
      
          @RequestMapping(value = "/dept/list",method = RequestMethod.GET)
      
          public List<Dept> list();
      
          @RequestMapping(value = "/dept/add",method = RequestMethod.POST)
      
          public boolean add(Dept dept);
      
      }
      ~~~

   3. mvn clean install

   4. consumer-dept-feign-80工程修改Controller，添加上一步新建的DeptClientService接口

      ~~~java
      package com.lovel.heart.controller;
      
      import com.lovel.heart.entities.Dept;
      import com.lovel.heart.service.DeptClientService;
      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.web.bind.annotation.PathVariable;
      import org.springframework.web.bind.annotation.RequestMapping;
      import org.springframework.web.bind.annotation.RestController;
      
      import java.util.List;
      
      @RestController
      public class DeptControllerConsumerFeign {
      
          @Autowired
          private DeptClientService service;
      
          @RequestMapping(value = "/consumer/dept/get/{id}")
          public Dept get(@PathVariable("id") Long id) {
              return this.service.get(id);
          }
      
          @RequestMapping(value = "/consumer/dept/list")
          public List<Dept> list() {
              return this.service.list();
          }
      
          @RequestMapping(value = "/consumer/dept/add")
          public Object add(Dept dept) {
              return this.service.add(dept);
          }
      }
      ~~~

   5. consumer-dept-feign-80工程修改主启动类

      ~~~java
      package com.lovel.heart;
      
      import org.springframework.boot.SpringApplication;
      import org.springframework.boot.autoconfigure.SpringBootApplication;
      import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
      import org.springframework.cloud.netflix.feign.EnableFeignClients;
      
      @SpringBootApplication
      @EnableEurekaClient
      @EnableFeignClients(basePackages= {"com.lovel.heart"})
      public class DeptConsumerFeign80App {
      
          public static void main(String[] args) {
              SpringApplication.run(DeptConsumerFeign80App.class, args);
          }
      }
      ~~~

   6. 启动3个eureka集群,启动3个部门微服务8001/8002/8003,启动Feign

      ~~~http
      http://localhost/consumer/dept/list
      ~~~

   Feign通过接口的方法调用Rest服务（之前是Ribbon+RestTemplate），

   该请求发送给Eureka服务器（http://LB-TEST-DEPT/dept/list）,

   通过Feign直接找到服务接口，由于在进行服务调用的时候融合了Ribbon技术，所以也支持负载均衡作用。



#### 3.5 Feign 分析

启用Feign不一样的地方就是使用Feign的地方使用了`@Feignclient`注解，我们看一下它的源代码

   ~~~java
   @Target({ElementType.TYPE})
   @Retention(RetentionPolicy.RUNTIME)
   @Documented
   public @interface FeignClient {
       @AliasFor("name")
       String value() default "";
   
       /** @deprecated */
       @Deprecated
       String serviceId() default "";
   
       @AliasFor("value")
       String name() default "";
   
       String qualifier() default "";
   
       String url() default "";
   
       boolean decode404() default false;
   
       Class<?>[] configuration() default {};
   
       Class<?> fallback() default void.class;
   
       Class<?> fallbackFactory() default void.class;
   
       String path() default "";
   
       boolean primary() default true;
   }
   ~~~

FeignClient注解被@Target(ElementType.TYPE)修饰，表示FeignClient注解的作用目标在接口上；

feign 用于声明具有该接口的REST客户端的接口的注释应该是创建（例如用于自动连接到另一个组件。 如果功能区可用，那将是 用于负载平衡后端请求，并且可以配置负载平衡器使用与服务端相同名称（即值）。

其中value()和name()一样，是被调用的 service的名称。 url(),直接填写硬编码的url,decode404()即404是否被解码，还是抛异常；configuration()，标明FeignClient的配置类，默认的配置类为FeignClientsConfiguration类，可以覆盖Decoder、Encoder和Contract等信息，进行自定义配置。fallback(),填写熔断器的信息类。

默认配置类如下

~~~java
@Configuration
public class FeignClientsConfiguration {
    @Autowired
    private ObjectFactory<HttpMessageConverters> messageConverters;
    @Autowired(
        required = false
    )
    private List<AnnotatedParameterProcessor> parameterProcessors = new ArrayList();
    @Autowired(
        required = false
    )
    private List<FeignFormatterRegistrar> feignFormatterRegistrars = new ArrayList();
    @Autowired(
        required = false
    )
    private Logger logger;

    public FeignClientsConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    public Decoder feignDecoder() {
        return new ResponseEntityDecoder(new SpringDecoder(this.messageConverters));
    }

    @Bean
    @ConditionalOnMissingBean
    public Encoder feignEncoder() {
        return new SpringEncoder(this.messageConverters);
    }

    @Bean
    @ConditionalOnMissingBean
    public Contract feignContract(ConversionService feignConversionService) {
        return new SpringMvcContract(this.parameterProcessors, feignConversionService);
    }

    @Bean
    public FormattingConversionService feignConversionService() {
        FormattingConversionService conversionService = new DefaultFormattingConversionService();
        Iterator var2 = this.feignFormatterRegistrars.iterator();

        while(var2.hasNext()) {
            FeignFormatterRegistrar feignFormatterRegistrar = (FeignFormatterRegistrar)var2.next();
            feignFormatterRegistrar.registerFormatters(conversionService);
        }

        return conversionService;
    }

    @Bean
    @ConditionalOnMissingBean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public Builder feignBuilder(Retryer retryer) {
        return Feign.builder().retryer(retryer);
    }

    @Bean
    @ConditionalOnMissingBean({FeignLoggerFactory.class})
    public FeignLoggerFactory feignLoggerFactory() {
        return new DefaultFeignLoggerFactory(this.logger);
    }

    @Configuration
    @ConditionalOnClass({HystrixCommand.class, HystrixFeign.class})
    protected static class HystrixFeignConfiguration {
        protected HystrixFeignConfiguration() {
        }

        @Bean
        @Scope("prototype")
        @ConditionalOnMissingBean
        @ConditionalOnProperty(
            name = {"feign.hystrix.enabled"},
            matchIfMissing = false
        )
        public Builder feignHystrixBuilder() {
            return HystrixFeign.builder();
        }
    }
}
~~~

这个类注入了很多的相关配置的bean，包括feignRetryer、FeignLoggerFactory、FormattingConversionService等,其中还包括了Decoder、Encoder、Contract，如果这三个bean在没有注入的情况下，会自动注入默认的配置。

用户也可以自定义配置比如想要修改重试次数按下面写一个配置注入即可

~~~java
@Configuration
public class FeignConfig {
 
    @Bean
    public Retryer feignRetryer() {
        // 重试间隔为100ms，最大重试时间为1s,重试次数为5次。
        return new Retryer.Default(100, SECONDS.toMillis(1), 5);
    }
~~~

##### 3.5.1 feign工作原理

feign是一个伪客户端，即它不做任何的请求处理。Feign通过处理注解生成request，从而实现简化HTTP API开发的目的，即开发人员可以使用注解的方式定制request api模板，在发送http request请求之前，feign通过处理注解的方式替换掉request模板中的参数，这种实现方式显得更为直接、可理解。

通过包扫描注入FeignClient的bean，该源码在FeignClientsRegistrar类： 
首先在启动配置上检查是否有@EnableFeignClients注解，如果有该注解，则开启包扫描，扫描被@FeignClient注解接口。代码如下：

~~~java
private void registerDefaultConfiguration(AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
        Map<String, Object> defaultAttrs = metadata
                .getAnnotationAttributes(EnableFeignClients.class.getName(), true);
 
        if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
            String name;
            if (metadata.hasEnclosingClass()) {
                name = "default." + metadata.getEnclosingClassName();
            }
            else {
                name = "default." + metadata.getClassName();
            }
            registerClientConfiguration(registry, name,
                    defaultAttrs.get("defaultConfiguration"));
        }
    }
~~~

程序启动后通过包扫描，当类有@FeignClient注解，将注解的信息取出，连同类名一起取出，赋给BeanDefinitionBuilder，然后根据BeanDefinitionBuilder得到beanDefinition，最后beanDefinition注入到ioc容器中，源码如下：

~~~java
public void registerFeignClients(AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(this.resourceLoader);
 
        Set<String> basePackages;
 
        Map<String, Object> attrs = metadata
                .getAnnotationAttributes(EnableFeignClients.class.getName());
        AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(
                FeignClient.class);
        final Class<?>[] clients = attrs == null ? null
                : (Class<?>[]) attrs.get("clients");
        if (clients == null || clients.length == 0) {
            scanner.addIncludeFilter(annotationTypeFilter);
            basePackages = getBasePackages(metadata);
        }
        else {
            final Set<String> clientClasses = new HashSet<>();
            basePackages = new HashSet<>();
            for (Class<?> clazz : clients) {
                basePackages.add(ClassUtils.getPackageName(clazz));
                clientClasses.add(clazz.getCanonicalName());
            }
            AbstractClassTestingTypeFilter filter = new AbstractClassTestingTypeFilter() {
                @Override
                protected boolean match(ClassMetadata metadata) {
                    String cleaned = metadata.getClassName().replaceAll("\\$", ".");
                    return clientClasses.contains(cleaned);
                }
            };
            scanner.addIncludeFilter(
                    new AllTypeFilter(Arrays.asList(filter, annotationTypeFilter)));
        }
 
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidateComponents = scanner
                    .findCandidateComponents(basePackage);
            for (BeanDefinition candidateComponent : candidateComponents) {
                if (candidateComponent instanceof AnnotatedBeanDefinition) {
                    // verify annotated class is an interface
                    AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                    AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                    Assert.isTrue(annotationMetadata.isInterface(),
                            "@FeignClient can only be specified on an interface");
 
                    Map<String, Object> attributes = annotationMetadata
                            .getAnnotationAttributes(
                                    FeignClient.class.getCanonicalName());
 
                    String name = getClientName(attributes);
                    registerClientConfiguration(registry, name,
                            attributes.get("configuration"));
 
                    registerFeignClient(registry, annotationMetadata, attributes);
                }
            }
        }
    }
 
 
private void registerFeignClient(BeanDefinitionRegistry registry,
            AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        BeanDefinitionBuilder definition = BeanDefinitionBuilder
                .genericBeanDefinition(FeignClientFactoryBean.class);
        validate(attributes);
        definition.addPropertyValue("url", getUrl(attributes));
        definition.addPropertyValue("path", getPath(attributes));
        String name = getName(attributes);
        definition.addPropertyValue("name", name);
        definition.addPropertyValue("type", className);
        definition.addPropertyValue("decode404", attributes.get("decode404"));
        definition.addPropertyValue("fallback", attributes.get("fallback"));
        definition.addPropertyValue("fallbackFactory", attributes.get("fallbackFactory"));
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
 
        String alias = name + "FeignClient";
        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
 
        boolean primary = (Boolean)attributes.get("primary"); // has a default, won't be null
 
        beanDefinition.setPrimary(primary);
 
        String qualifier = getQualifier(attributes);
        if (StringUtils.hasText(qualifier)) {
            alias = qualifier;
        }
 
        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
                new String[] { alias });
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
    }
~~~

注入bean之后，通过jdk的代理，当请求Feign Client的方法时会被拦截，代码在ReflectiveFeign类，代码如下：

~~~java
 public <T> T newInstance(Target<T> target) {
    Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
    Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<Method, MethodHandler>();
    List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();
 
    for (Method method : target.type().getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      } else if(Util.isDefault(method)) {
        DefaultMethodHandler handler = new DefaultMethodHandler(method);
        defaultMethodHandlers.add(handler);
        methodToHandler.put(method, handler);
      } else {
        methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
      }
    }
    InvocationHandler handler = factory.create(target, methodToHandler);
    T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[]{target.type()}, handler);
 
    for(DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
      defaultMethodHandler.bindTo(proxy);
    }
    return proxy;
  }
~~~

在SynchronousMethodHandler类进行拦截处理，当被FeignClient的方法被拦截会根据参数生成RequestTemplate对象，该对象就是http请求的模板，代码如下：

~~~java
 @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.create(argv);
    Retryer retryer = this.retryer.clone();
    while (true) {
      try {
        return executeAndDecode(template);
      } catch (RetryableException e) {
        retryer.continueOrPropagate(e);
        if (logLevel != Logger.Level.NONE) {
          logger.logRetry(metadata.configKey(), logLevel);
        }
        continue;
      }
    }
  }
~~~

其中有个executeAndDecode()方法，该方法是通RequestTemplate生成Request请求对象，然后根据用client获取response。

~~~java
  Object executeAndDecode(RequestTemplate template) throws Throwable {
    Request request = targetRequest(template);
    ...//省略代码
    response = client.execute(request, options);
    ...//省略代码
 
}

~~~

#### 3.6 Feign Client组件

Client组件是一个非常重要的组件，Feign最终发送request请求以及接收response响应，都是由Client组件完成的，其中Client的实现类，只要有Client.Default，该类由HttpURLConnnection实现网络请求，另外还支持HttpClient、Okhttp.

首先来看以下在FeignRibbonClient的自动配置类，FeignRibbonClientAutoConfiguration ，主要在工程启动的时候注入一些bean,其代码如下：

~~~java
@ConditionalOnClass({ ILoadBalancer.class, Feign.class })
@Configuration
@AutoConfigureBefore(FeignAutoConfiguration.class)
public class FeignRibbonClientAutoConfiguration {
 
@Bean
    @ConditionalOnMissingBean
    public Client feignClient(CachingSpringLoadBalancerFactory cachingFactory,
            SpringClientFactory clientFactory) {
        return new LoadBalancerFeignClient(new Client.Default(null, null),
                cachingFactory, clientFactory);
    } 
}
~~~

在缺失配置feignClient的情况下，会自动注入new Client.Default(),跟踪Client.Default()源码，它使用的网络请求框架为HttpURLConnection，代码如下：

~~~java
  @Override
    public Response execute(Request request, Options options) throws IOException {
      HttpURLConnection connection = convertAndSend(request, options);
      return convertResponse(connection).toBuilder().request(request).build();
    }
~~~

#### 3.7 feign负载均衡的实现

通过上述的FeignRibbonClientAutoConfiguration类配置Client的类型(httpurlconnection，okhttp和httpclient)时候，可知最终向容器注入的是LoadBalancerFeignClient，即负载均衡客户端。现在来看下LoadBalancerFeignClient的代码：

~~~java
    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        try {
            URI asUri = URI.create(request.url());
            String clientName = asUri.getHost();
            URI uriWithoutHost = cleanUrl(request.url(), clientName);
            FeignLoadBalancer.RibbonRequest ribbonRequest = new FeignLoadBalancer.RibbonRequest(
                    this.delegate, request, uriWithoutHost);
 
            IClientConfig requestConfig = getClientConfig(options, clientName);
            return lbClient(clientName).executeWithLoadBalancer(ribbonRequest,
                    requestConfig).toResponse();
        }
        catch (ClientException e) {
            IOException io = findIOException(e);
            if (io != null) {
                throw io;
            }
            throw new RuntimeException(e);
        }
    }
~~~

其中有个executeWithLoadBalancer()方法，即通过负载均衡的方式请求。

~~~java
  public T executeWithLoadBalancer(final S request, final IClientConfig requestConfig) throws ClientException {
        RequestSpecificRetryHandler handler = getRequestSpecificRetryHandler(request, requestConfig);
        LoadBalancerCommand<T> command = LoadBalancerCommand.<T>builder()
                .withLoadBalancerContext(this)
                .withRetryHandler(handler)
                .withLoadBalancerURI(request.getUri())
                .build();
 
        try {
            return command.submit(
                new ServerOperation<T>() {
                    @Override
                    public Observable<T> call(Server server) {
                        URI finalUri = reconstructURIWithServer(server, request.getUri());
                        S requestForServer = (S) request.replaceUri(finalUri);
                        try {
                            return Observable.just(AbstractLoadBalancerAwareClient.this.execute(requestForServer, requestConfig));
                        } 
                        catch (Exception e) {
                            return Observable.error(e);
                        }
                    }
                })
                .toBlocking()
                .single();
        } catch (Exception e) {
            Throwable t = e.getCause();
            if (t instanceof ClientException) {
                throw (ClientException) t;
            } else {
                throw new ClientException(e);
            }
        } 
    }   
~~~

其中服务在submit()方法上，点击submit进入具体的方法,这个方法是LoadBalancerCommand的方法：

~~~java
     Observable<T> o = 
                (server == null ? selectServer() : Observable.just(server))
                .concatMap(new Func1<Server, Observable<T>>() {
                    @Override
                    // Called for each server being selected
                    public Observable<T> call(Server server) {
                        context.setServer(server); 
        }}
~~~

上述代码中有个selectServe()，该方法是选择服务的进行负载均衡的方法，代码如下：

~~~java
    private Observable<Server> selectServer() {
        return Observable.create(new OnSubscribe<Server>() {
            @Override
            public void call(Subscriber<? super Server> next) {
                try {
                    Server server = loadBalancerContext.getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);   
                    next.onNext(server);
                    next.onCompleted();
                } catch (Exception e) {
                    next.onError(e);
                }
            }
        });
    }
~~~

最终负载均衡交给loadBalancerContext来处理，即之前讲述的Ribbon。



总到来说，Feign的源码实现的过程如下：

- 首先通过@EnableFeignCleints注解开启FeignCleint
- 根据Feign的规则实现接口，并加@FeignCleint注解
- 程序启动后，会进行包扫描，扫描所有的@ FeignCleint的注解的类，并将这些信息注入到ioc容器中。
- 当接口的方法被调用，通过jdk的代理，来生成具体的RequesTemplate
- RequesTemplate在生成Request
- Request交给Client去处理，其中Client可以是HttpUrlConnection、HttpClient也可以是Okhttp
- 最后Client被封装到LoadBalanceClient类，这个类结合类Ribbon做到了负载均衡。



#### 3.8 zuul

![mark](http://piiw75qxc.bkt.clouddn.com/blog/20181220/9lbnG4UwRB0P.png?imageslim)

我们按照之前的思路来进行搭建我们的服务，再搭配配置中心，提供传统对外服务，大致结果是类似于这样的情况，但是这样的架构存在以下问题

- 首先，破坏了服务无状态特点。为了保证对外服务的安全性，我们需要实现对服务访问的权限控制，而开放服务的权限控制机制将会贯穿并污染整个开放服务的业务逻辑，这会带来的最直接问题是，破坏了服务集群中REST API无状态的特点。从具体开发和测试的角度来说，在工作中除了要考虑实际的业务逻辑之外，还需要额外可续对接口访问的控制处理。
- 其次，无法直接复用既有接口。当我们需要对一个即有的集群内访问接口，实现外部服务访问时，我们不得不通过在原有接口上增加校验逻辑，或增加一个代理调用来实现权限控制，无法直接复用原有的接口。

这就需要借助我们的服务网关来解决这样的问题。所以zuul的主要任务并不是用来做负载均衡，负载均衡只是其功能的一小部分。

Zuul包含了对请求的路由和过滤两个最主要的功能：

其中路由功能负责将外部请求转发到具体的微服务实例上，是实现外部访问统一入口的基础而过滤器功能则负责对请求的处理过程进行干预，是实现请求校验、服务聚合等功能的基础.

Zuul和Eureka进行整合，将Zuul自身注册为Eureka服务治理下的应用，同时从Eureka中获得其他微服务的消息，也即以后的访问微服务都是通过Zuul跳转后获得。

##### 3.8.1 构建一个zuul服务

1. 新建Module模块zuul-gateway-9527

2. 修改pom.xml文件

   ~~~properties
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <parent>
           <artifactId>lb-test</artifactId>
           <groupId>com.lovel.heart</groupId>
           <version>1.0-SNAPSHOT</version>
       </parent>
       <modelVersion>4.0.0</modelVersion>
   
       <artifactId>zuul-gateway-9527</artifactId>
   
       <dependencies>
           <!-- zuul路由网关 -->
           <dependency>
               <groupId>org.springframework.cloud</groupId>
               <artifactId>spring-cloud-starter-zuul</artifactId>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.cloud</groupId>
               <artifactId>spring-cloud-starter-eureka</artifactId>
           </dependency>
   
           <!-- actuator监控 -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-actuator</artifactId>
           </dependency>
   
           <!--  hystrix容错-->
           <dependency>
               <groupId>org.springframework.cloud</groupId>
               <artifactId>spring-cloud-starter-hystrix</artifactId>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.cloud</groupId>
               <artifactId>spring-cloud-starter-config</artifactId>
           </dependency>
   
           <!-- 日常标配 -->
           <dependency>
               <groupId>com.lovel.heart</groupId>
               <artifactId>test-api</artifactId>
               <version>1.0-SNAPSHOT</version>
               <scope>compile</scope>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-jetty</artifactId>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-web</artifactId>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-test</artifactId>
           </dependency>
   
           <!-- 热部署插件 -->
           <dependency>
               <groupId>org.springframework</groupId>
               <artifactId>springloaded</artifactId>
           </dependency>
   
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-devtools</artifactId>
           </dependency>
       </dependencies>
   </project>
   ~~~

3. 新建application.yam

   ~~~yaml
   server:
     port: 9999
   
   spring:
     application:
       name: zuul-gateway
   
   zuul:
     prefix: /heart    # 同一前缀
     ignored-services: "*"     # 原真是服务名忽略，只能用代理名称
     routes:
       mydept.serviceId: lb-test-dept
       mydept.path: /lovel/**      # 代理名称映射
   
   eureka:
     client:
       service-url:
         defaultZone: http://eureka7001.com:7001/eureka,http://eureka7002.com:7002/eureka,http://eureka7003.com:7003/eureka
     instance:
       instance-id: gateway-9527.com
       prefer-ip-address: true
   
   info:
     app.name: heart-lbtest
     company.name: heart.lovel.com
     build.artifactId: $project.artifactId$
     build.version: $project.version$
   ~~~

4. 修改host文件添加一下内容

   ~~~http
   127.0.0.1  myzuul.com
   ~~~

5. 创建主启动类

   ~~~java
   package com.lovel.heart;
   
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
   
   @SpringBootApplication
   @EnableZuulProxy
   public class Zuul9527App {
       public static void main(String[] args) {
           SpringApplication.run(Zuul9527App.class, args);
       }
   }
   ~~~

6. 启动三个eureka集群，一个服务提供类provider-dept-8001，启动路由

   ~~~http
   http://myzuul.com:9999/heart/lovel/dept/get/2
   ~~~




#### 3.8 zuul分析

这一部分还没做.....

 

