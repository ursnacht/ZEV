# Tasks

## Next Tasks

* Possibility to upload an CSV-file and store the database
  * ~~create database table messwerte with columns~~
    * ~~ID using a sequence~~
    * ~~zeit as datetime~~
    * ~~total as double~~
    * ~~zev as double~~
  * ~~create entity and repository classes~~
  * create POST REST endpoint to upload a CSV file
    * use a JSON structure 
      * date
      * typ : use enum EinheitTyp
      * csv file
    * store the csv file messwerte
      * zeit is composed by the given date and the time 00:15 incrementing for each line by 15 minutes
      * typ is given in the json
      * second column in total
      * third column in zev
  * create angular gui to
    * choose date
    * choose typ from EinheitTyp
    * choose file
    * add button to upload and store

## Completed

- Initial Maven project setup
- SolarDistribution algorithm
- JUnit tests
- Docker Compose with PostgreSQL
- Flyway migrations
- Upgrade to Java 17
- Database table einheit with ID (sequence) and NAME columns
- Einheit entity class with Hibernate/JPA
- Database table messwerte with entity and repository
