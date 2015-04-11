package main

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/nu7hatch/gouuid"
)

/*
TODO(broluwo):
1. User creation endpoint
2. Handling BLOBs - We take in the metadata and store that but put the actual file in the filesystem
and just load it when necessary
3. Prepare statements for all the api end points
*/

const (
	imgCap       = "/cap/img"
	vidCap       = "/cap/vid"
	userEndpoint = "/user"
	dbHost       = "localhost"
	dbName       = "bitcamp"
	sslMode      = "disable"
	folderName   = "datafs"
)

//Response what the upload sends me
type Response struct {
	uploader sUID
	//I'm assuming that this is going to be base64 encoded despite the file size hit
	payload string
	//Extension will be inferred from the endpoint used
	lat  string
	long string
}

//Capsule is the Capsule that capsules
//TODO(broluwo): Flesh out Capsule
type Capsule struct {
	Lat  string
	Long string
}

//User is important and requires description
//When a User is created they send a post to the /user endpoint
//and i will send them back a uid that's now their uniqueid with which they can sign files
//they will also get a shareuid which is how i will see who's friends and whose not
type User struct {
	uid      sUID
	shareUID sUID
	//These are the confirmed friends in the sUID which will be searchable
	Friends []sUID
	//These are the pending sUID
	Pending   []sUID
	ContentID []string
}
type sUID string

var (
	//Assumes it's set up with gen user accessibility
	db     *sql.DB
	dbUser = os.Getenv("USER")
)

func init() {
	initDB()
}

//initDB Dials into the database
func initDB() {
	var err error
	dbInfo := fmt.Sprintf("host=%s user=%s dbname=%s sslmode=%s", dbHost, dbUser, dbName, sslMode)
	db, err = sql.Open("postgres", dbInfo)
	if err != nil {
		log.Fatalf("Database failed to initiate, %v", err)
	}

}

func main() {
	//Make sure we close the db connection when we are done.
	defer db.Close()
	//Pass off the handling to individual functions, but more correctly a mux Router
	http.Handle("/", initHandlers())
	log.Fatalln(http.ListenAndServe(":8080", nil))
}

func initHandlers() (router *mux.Router) {
	router = mux.NewRouter()
	router.HandleFunc(userEndpoint, handleUser).Methods("POST")
	router.HandleFunc(imgCap, handleImgCap).Methods("POST", "DELETE")
	router.HandleFunc(vidCap, handleVidCap).Methods("POST", "DELETE")
	return
}

func handleImgCap(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case "POST":
		var res Response
		err := ReadJSON(req, &res)
		checkFatalErr(err, "Wat happened ")
		dDec, err := base64.StdEncoding.DecodeString(res.payload)
		//We may switch to gzip encode
		checkPrintErr(err, "Something wrong")
		err = os.Mkdir(folderName, os.ModePerm)
		checkPrintErr(err, "Dir already exists")
		contentID, err := uuid.NewV4()
		checkPrintErr(err, "Unable to gen uuid")
		//		if strings.Contains(req.Url,img)
		ioutil.WriteFile("./"+folderName+"/"+contentID.String(), dDec, 0755)
		//now we write the file into the new
		addFileToUser(contentID.String(), res.uploader)
		//Tell them that we got their files
		http.Error(w, http.StatusText(http.StatusCreated), http.StatusCreated)
		break
	}

}

func handleVidCap(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case "POST":
		var res Response
		err := ReadJSON(req, &res)
		checkFatalErr(err, "Wat happened ")
		dDec, err := base64.StdEncoding.DecodeString(res.payload)
		//We may switch to gzip encode
		checkPrintErr(err, "Something wrong")
		err = os.Mkdir(folderName, os.ModePerm)
		checkPrintErr(err, "Dir already exists")
		contentID, err := uuid.NewV4()
		checkPrintErr(err, "Unable to gen uuid")
		ioutil.WriteFile("./"+folderName+"/"+contentID.String(), dDec, 0755)
		//now we write the file into the new
		addFileToUser(contentID.String(), res.uploader)
		//Tell them that we got their files
		http.Error(w, http.StatusText(http.StatusCreated), http.StatusCreated)
		break
	}

}

func addFileToUser(fileName string, uploader sUID) {
	//We need to do an insert into the user table now
}
func handleUser(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case "POST":
		break
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

func checkPrintErr(err error, msg string) {
	if err != nil {
		log.Println(msg, err)
	}
}
func checkFatalErr(err error, msg string) {
	if err != nil {
		log.Fatalln(msg, err)
	}
}
