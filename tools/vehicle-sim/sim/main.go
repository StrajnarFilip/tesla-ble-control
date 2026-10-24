package main

import (
	"bufio"
	"encoding/hex"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/keys"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/vcsec"
)

const maxMessageSize = 1024

type hexKeys [][]byte

func (h *hexKeys) String() string { return fmt.Sprintf("%d keys", len(*h)) }

func (h *hexKeys) Set(value string) error {
	key, err := hex.DecodeString(strings.TrimSpace(value))
	if err != nil || len(key) != 65 {
		return fmt.Errorf("expected a hex-encoded uncompressed P-256 public key")
	}
	*h = append(*h, key)
	return nil
}

func main() {
	if len(os.Args) > 1 && os.Args[1] == "golden" {
		os.Exit(runGolden())
	}

	var owners, drivers hexKeys
	listen := flag.String("listen", "127.0.0.1:0", "TCP address to listen on")
	vin := flag.String("vin", "5YJ3E1EA1PF000001", "vehicle identification number")
	asleep := flag.Bool("asleep", false, "start with infotainment asleep")
	wakeDelay := flag.Duration("wake-delay", 1500*time.Millisecond, "time infotainment takes to wake")
	pairingDelay := flag.Duration("pairing-delay", 2*time.Second, "delay before a pairing request is approved (negative: never)")
	intermediate := flag.Bool("intermediate-replies", false, "VCSEC sends a non-terminal status before each result")
	duplicate := flag.Bool("duplicate-replies", false, "send every reply twice")
	dropEvery := flag.Int("drop-every", 0, "silently drop every Nth request")
	busy := flag.Int("busy", 0, "answer the first N infotainment commands with BUSY")
	verbose := flag.Bool("v", false, "log protocol events to stderr")
	flag.Var(&owners, "owner", "public key (hex) enrolled with the owner role; repeatable")
	flag.Var(&drivers, "driver", "public key (hex) enrolled with the driver role; repeatable")
	flag.Parse()

	logf := func(string, ...any) {}
	if *verbose {
		logger := log.New(os.Stderr, "[vehicle-sim] ", log.Ltime|log.Lmicroseconds)
		logf = logger.Printf
	}
	vehicle, err := NewVehicle(Options{
		VIN:                  *vin,
		StartAsleep:          *asleep,
		WakeDelay:            *wakeDelay,
		PairingApprovalDelay: *pairingDelay,
		IntermediateReplies:  *intermediate,
		DuplicateReplies:     *duplicate,
		DropEveryNth:         *dropEvery,
		BusyReplies:          *busy,
		Logf:                 logf,
	})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	for _, key := range owners {
		vehicle.Enroll(key, keys.Role_ROLE_OWNER, vcsec.KeyFormFactor_KEY_FORM_FACTOR_ANDROID_DEVICE)
	}
	for _, key := range drivers {
		vehicle.Enroll(key, keys.Role_ROLE_DRIVER, vcsec.KeyFormFactor_KEY_FORM_FACTOR_ANDROID_DEVICE)
	}

	listener, err := net.Listen("tcp", *listen)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Printf("LISTENING %d\n", listener.Addr().(*net.TCPAddr).Port)

	go control(vehicle)
	for {
		conn, err := listener.Accept()
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		go serve(vehicle, conn)
	}
}

// control reads commands from stdin. The simulator exits when stdin closes, so it never outlives
// the test process that started it.
func control(vehicle *Vehicle) {
	scanner := bufio.NewScanner(os.Stdin)
	for scanner.Scan() {
		switch strings.TrimSpace(scanner.Text()) {
		case "reboot":
			vehicle.Reboot()
		case "sleep":
			vehicle.Sleep()
		case "quit":
			os.Exit(0)
		}
		fmt.Println("OK")
	}
	os.Exit(0)
}

// serve speaks the BLE wire format: each message is preceded by its two-byte big-endian length.
func serve(vehicle *Vehicle, conn net.Conn) {
	defer conn.Close()
	reader := bufio.NewReader(conn)
	var writeLock sync.Mutex
	header := make([]byte, 2)
	for {
		if _, err := io.ReadFull(reader, header); err != nil {
			return
		}
		length := int(header[0])<<8 | int(header[1])
		if length > maxMessageSize {
			return
		}
		frame := make([]byte, length)
		if _, err := io.ReadFull(reader, frame); err != nil {
			return
		}
		for _, reply := range vehicle.HandleFrame(frame) {
			writeLock.Lock()
			_, err := conn.Write(append([]byte{byte(len(reply) >> 8), byte(len(reply))}, reply...))
			writeLock.Unlock()
			if err != nil {
				return
			}
		}
	}
}
