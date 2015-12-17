@Grab("org.grails:gorm-mongodb-spring-boot:1.1.0.RELEASE")
@Grab("org.mongodb:mongo-java-driver:2.12.2")
@Grab("com.google.code.gson:gson:2.5")

import grails.persistence.*
import grails.mongodb.geo.*
import org.bson.types.ObjectId
import com.mongodb.BasicDBObject
import org.springframework.http.*
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import static org.springframework.web.bind.annotation.RequestMethod.*

import com.google.gson.*

// this is required because gorm-mongodb-spring-boot creates a bean named mongoMappingContext
// that prevents MongoDataAutoConfiguration from creating its own bean with the same name
@Bean
MongoMappingContext springDataMongoMappingContext() {
    return new MongoMappingContext()
}

// Rest controller
@RestController
class CityController {

    @RequestMapping(value="/", method = GET)
    List index() {
        City.list().collect { [name: it.name] }
    }

    @RequestMapping(value="/near/{cityName}", method = GET)
    ResponseEntity near(@PathVariable String cityName) {
        def city = City.where { name == cityName }.find()
        if(city) {
            List<City> closest = City.findAllByLocationNear(city.location)
            return new ResponseEntity([name: closest[1].name,
                                       location: closest[1].location], HttpStatus.OK)
        }
        else {
            return new ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    @RequestMapping(value="/city", method = POST)
    ResponseEntity save(@RequestBody String json) {

        /**
         * This should be done in (@RequestBody City city) but
         * cant get the JSON to parse due to including Point
         */
        JsonParser jsonParser = new JsonParser()
        JsonElement element = jsonParser.parse(json)
        JsonObject object = element.getAsJsonObject()
        JsonElement name = object.get("name")
        JsonElement location = object.get("location")
        JsonObject locObj = location.getAsJsonObject()
        JsonElement x = locObj.get("x")
        JsonElement y = locObj.get("y")
        def city = new City(name: name.getAsString(),
                location: Point.valueOf( [x.getAsFloat(), y.getAsFloat()] ) )
        /**
         * end of fudge
         */

        city.save(flush: true, failOnError: true)

        return new ResponseEntity([name: city.name, location: city.location],
                HttpStatus.CREATED)
    }

    // Just set up some test data
    @PostConstruct
    void populateCities() {
        City.withTransaction{
            City.collection.remove(new BasicDBObject())
            City.saveAll( [ new City(name:"London", location: Point.valueOf( [-0.125487, 51.508515] ) ),
                            new City(name:"Paris", location: Point.valueOf( [2.352222, 48.856614] ) ),
                            new City(name:"New York", location: Point.valueOf( [-74.005973, 40.714353] ) ),
                            new City(name:"San Francisco", location: Point.valueOf( [-122.419416, 37.774929] ) ) ] )
        }
    }
}

// GORM entity object
@Entity
class City {
    ObjectId id
    String name
    Point location

    static constraints = {
        name blank:false
        location nullable:false
    }

    static mapping = {
        location geoIndex:'2dsphere'
    }
}

