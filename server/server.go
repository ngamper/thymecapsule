package main

import (
	"bytes"
	"encoding/base64"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/nu7hatch/gouuid"
	"gopkg.in/mgo.v2"
	"gopkg.in/mgo.v2/bson"
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
	dbURI        = "127.0.0.1"
	sslMode      = "disable"
	folderName   = "datafs"
)

var (
	s               = Server{}
	CollectionNames = []string{"user", "cap"}
	userIndex       = mgo.Index{
		Unique:     true,
		DropDups:   true,
		Background: true,
		Sparse:     true,
		Name:       "userIndex",
	}

	indices = []mgo.Index{userIndex}
)

//Server is the server
type Server struct {
	Session *mgo.Session // The main session we'll we be cloning
	DBURI   string       // Where the DB is on the network
	dbName  string       // Name of the MongoDB
}

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

func init() {
	initDB()
}

func main() {
	//Make sure we kill conn to db
	defer s.Session.Close()
	//Pass off the handling to individual functions, but more correctly a mux Router
	http.Handle("/", initHandlers())
	log.Fatalln(http.ListenAndServe(":8080", nil))
}
func initDB() {
	s.DBURI = dbURI
	s.dbName = dbName
	s.getSession()
	s.Session.SetSafe(&mgo.Safe{})
	s.Session.SetMode(mgo.Monotonic, true)
	cNames, errors := EnsureIndex(CollectionNames, indices...)
	for k, err := range errors {
		if err != nil {
			log.Fatalf("Indices not taking for %v;%v\n", cNames[k], err)
		}
	}
}

//EnsureIndex ensures that when we store things, we get the expected results
func EnsureIndex(collectionNames []string, indices ...mgo.Index) (s []string, e []error) {
	for j, k := range indices {
		function := func(c *mgo.Collection) error {
			return c.EnsureIndex(k)
		}
		err := withCollection(collectionNames[j], function)
		if err != nil {
			s = append(s, collectionNames[j])
			e = append(e, err)
		}
	}
	return
}

func (s *Server) getSession() *mgo.Session {
	if s.Session == nil {
		var err error
		dialInfo := &mgo.DialInfo{
			Addrs:    []string{s.DBURI},
			Direct:   true,
			FailFast: false,
		}
		s.Session, err = mgo.DialWithInfo(dialInfo)
		if err != nil {
			log.Fatalf("Can't find MongoDB. Is it started? %v\n", err)
		}
	}
	// Returns a copy of the session so we don't waste le resources? Doesn't reuse socket however
	return s.Session.Copy()
}

func withCollection(collection string, fn func(*mgo.Collection) error) error {
	session := s.getSession()
	defer session.Close()
	c := session.DB(s.dbName).C(collection)
	return fn(c)
}

//Insert datum into a specific collection
func Insert(collectionName string, values ...interface{}) error {
	function := func(c *mgo.Collection) error {
		err := c.Insert(values...)
		if err != nil {
			log.Printf("Can't insert document, %v\n", err)
		}
		return err
	}
	return withCollection(collectionName, function)
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
		err := ReadBSON(req, &res)
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
		err := ReadBSON(req, &res)
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

//ReadBSON decodes BSON data into a provided struct
//which is passed in as a pointer
func ReadBSON(req *http.Request, i interface{}) error {
	defer req.Body.Close()
	buf := new(bytes.Buffer)
	buf.ReadFrom(req.Body)
	return bson.Unmarshal(buf.Bytes(), i)
}

// ServeBSON replies to the request with a BSON obj
func ServeBSON(w http.ResponseWriter, v interface{}) {
	//	doc := map[string]interface{}{"d": v}
	if data, err := bson.Marshal(v); err != nil {
		log.Printf("Error marshalling bson: %v", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
	} else {
		w.Header().Set("Content-Length", strconv.Itoa(len(data)))
		w.Header().Set("Content-Type", "application/bson; charset=utf-8")
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
