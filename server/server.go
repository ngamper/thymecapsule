package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"

	"github.com/gorilla/mux"
)

const (
	capEndpoint = "/cap"
	dbHost      = "localhost"
	dbName      = "bitcamp"
	sslMode     = "disable"
)

type (
	//Capsule is the Capsule that capsules
	//TODO(broluwo): Flesh out Capsule
	Capsule struct {
		Lat  string
		Long string
	}
	//User is important and requires description
	User struct {
		Friends   []User
		Pending   []User
		ContentID []string
	}
)

var (
	//Assumes it's set up with gen user accessibility
	db     *sql.DB
	dbUser = os.Getenv("USER")
)

func init() {
	initDB()
}

//Dials into the database
func initDB() {
	var err error
	dbInfo := fmt.Sprintf("host=%s dbname=%s sslmode=%s", dbHost, dbName, sslMode)
	db, err = sql.Open("postgres", dbInfo)
	if err != nil {
		log.Fatalf("Database failed to initiate, %v", err)
	}

}

func main() {
	defer db.Close()
	http.Handle("/", initHandlers())
	http.ListenAndServe(":8080", nil)
}

func initHandlers() *mux.Router {
	r := mux.NewRouter()
	r.HandleFunc(capEndpoint, handlePOSTEndpoint).Methods("POST", "DELETE")
	return r
}

func handlePOSTEndpoint(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case "POST":
		var cap Capsule
		err := ReadJSON(req, &cap)
		if err != nil {
			log.Fatalf("Wat happened ")
		}
		//Now the data should be decoded into the proper struct
	case "DELETE":
		//Please kill the data
	}

}

//ReadJSON decodes JSON data into a provided struct
//which is passed in as a pointer
func ReadJSON(req *http.Request, i interface{}) error {
	defer req.Body.Close()
	decoder := json.NewDecoder(req.Body)
	err := decoder.Decode(i)
	return err
}

// ServeJSON replies to the request with a JSON obj
func ServeJSON(w http.ResponseWriter, v interface{}) {
	//	doc := map[string]interface{}{"d": v}
	if data, err := json.Marshal(v); err != nil {
		log.Printf("Error marshalling json: %v", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
	} else {
		w.Header().Set("Content-Length", strconv.Itoa(len(data)))
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Write(data)
	}
}
