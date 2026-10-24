// Command vehicle-sim is a reference vehicle for testing BLE clients. It speaks the vehicle side of
// Tesla's protocol using Tesla's own authentication code (internal/authentication), so a client
// that works against it agrees with the official implementation byte for byte.
package main

import (
	"bytes"
	"crypto/rand"
	"crypto/sha1"
	"errors"
	"fmt"
	"sync"
	"time"

	"google.golang.org/protobuf/proto"

	"github.com/teslamotors/vehicle-command/internal/authentication"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/keys"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/signatures"
	universal "github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/universalmessage"
	"github.com/teslamotors/vehicle-command/pkg/protocol/protobuf/vcsec"
)

// Options control simulated behavior and fault injection.
type Options struct {
	VIN                  string
	StartAsleep          bool
	WakeDelay            time.Duration
	PairingApprovalDelay time.Duration // negative: never approve
	IntermediateReplies  bool          // VCSEC sends a non-terminal status before the result
	DuplicateReplies     bool          // every reply is sent twice (exercises anti-replay)
	DropEveryNth         int           // drop every Nth request without replying (0: never)
	BusyReplies          int           // answer this many infotainment commands with BUSY first
	Logf                 func(format string, args ...any)
}

type keyEntry struct {
	public     []byte
	role       keys.Role
	formFactor vcsec.KeyFormFactor
}

// Recorded is one payload the vehicle received, after decryption.
type Recorded struct {
	Kind    string // "vcsec", "carserver", "raw" (unrouted ToVCSECMessage) or "sessioninfo"
	Payload []byte
}

// Vehicle is the simulated car. All methods are safe for concurrent use.
type Vehicle struct {
	mu         sync.Mutex
	opts       Options
	domainKeys map[universal.Domain]authentication.ECDHPrivateKey
	verifiers  map[string]*authentication.Verifier
	keys       []keyEntry
	requests   int
	busyLeft   int
	recorded   []Recorded

	asleep      bool
	locked      bool
	frunkOpen   bool
	trunkOpen   bool
	tonneauOpen bool
	portOpen    bool
	windowsOpen bool
	climateOn   bool
	driverTemp  float32
	passTemp    float32
	limit       int32
	amps        int32
	charging    bool
	sentry      bool
	valet       bool
	seatLeft    int32
	seatRight   int32
	steering    bool
	volume      float32
}

// NewVehicle creates a vehicle with fresh domain keys.
func NewVehicle(opts Options) (*Vehicle, error) {
	if opts.Logf == nil {
		opts.Logf = func(string, ...any) {}
	}
	v := &Vehicle{
		opts:       opts,
		domainKeys: make(map[universal.Domain]authentication.ECDHPrivateKey),
		verifiers:  make(map[string]*authentication.Verifier),
		busyLeft:   opts.BusyReplies,
		asleep:     opts.StartAsleep,
		locked:     true,
		driverTemp: 21,
		passTemp:   21,
		limit:      80,
		amps:       16,
		volume:     3.5,
	}
	for _, domain := range []universal.Domain{universal.Domain_DOMAIN_VEHICLE_SECURITY, universal.Domain_DOMAIN_INFOTAINMENT} {
		key, err := authentication.NewECDHPrivateKey(rand.Reader)
		if err != nil {
			return nil, err
		}
		v.domainKeys[domain] = key
	}
	return v, nil
}

// Enroll whitelists a public key.
func (v *Vehicle) Enroll(public []byte, role keys.Role, formFactor vcsec.KeyFormFactor) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.enrollLocked(public, role, formFactor)
}

func (v *Vehicle) enrollLocked(public []byte, role keys.Role, formFactor vcsec.KeyFormFactor) {
	if v.findKey(public) >= 0 {
		return
	}
	v.keys = append(v.keys, keyEntry{public: append([]byte{}, public...), role: role, formFactor: formFactor})
}

func (v *Vehicle) findKey(public []byte) int {
	for i, k := range v.keys {
		if bytes.Equal(k.public, public) {
			return i
		}
	}
	return -1
}

// Reboot rotates both domains' epochs, as a vehicle reboot would; clients must resync.
func (v *Vehicle) Reboot() {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.verifiers = make(map[string]*authentication.Verifier)
	v.opts.Logf("rebooted: sessions discarded")
}

// Sleep puts infotainment to sleep; it stops answering until woken.
func (v *Vehicle) Sleep() {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.asleep = true
}

// TakeRecorded returns and clears the payloads received so far.
func (v *Vehicle) TakeRecorded() []Recorded {
	v.mu.Lock()
	defer v.mu.Unlock()
	out := v.recorded
	v.recorded = nil
	return out
}

func (v *Vehicle) record(kind string, payload []byte) {
	v.recorded = append(v.recorded, Recorded{Kind: kind, Payload: append([]byte{}, payload...)})
}

func (v *Vehicle) verifier(domain universal.Domain, public []byte) (*authentication.Verifier, error) {
	id := fmt.Sprintf("%d:%x", domain, public)
	if verifier, ok := v.verifiers[id]; ok {
		return verifier, nil
	}
	verifier, err := authentication.NewVerifier(v.domainKeys[domain], []byte(v.opts.VIN), domain, public)
	if err != nil {
		return nil, err
	}
	v.verifiers[id] = verifier
	return verifier, nil
}

// HandleFrame processes one message from a client and returns the encoded replies.
func (v *Vehicle) HandleFrame(frame []byte) [][]byte {
	v.mu.Lock()
	defer v.mu.Unlock()

	var message universal.RoutableMessage
	if err := proto.Unmarshal(frame, &message); err == nil && message.GetToDestination() != nil {
		var out [][]byte
		for _, reply := range v.handleRoutable(&message) {
			encoded, err := proto.Marshal(reply)
			if err != nil {
				v.opts.Logf("marshal reply: %s", err)
				continue
			}
			out = append(out, encoded)
			if v.opts.DuplicateReplies {
				out = append(out, encoded)
			}
		}
		return out
	}
	var toVCSEC vcsec.ToVCSECMessage
	if err := proto.Unmarshal(frame, &toVCSEC); err == nil && toVCSEC.GetSignedMessage() != nil {
		v.handlePresentKey(frame, &toVCSEC)
		return nil
	}
	v.opts.Logf("dropping unparseable frame (%d bytes)", len(frame))
	return nil
}

func (v *Vehicle) handleRoutable(req *universal.RoutableMessage) []*universal.RoutableMessage {
	domain := req.GetToDestination().GetDomain()
	if domain != universal.Domain_DOMAIN_VEHICLE_SECURITY && domain != universal.Domain_DOMAIN_INFOTAINMENT {
		return nil
	}
	v.requests++
	if v.opts.DropEveryNth > 0 && v.requests%v.opts.DropEveryNth == 0 {
		v.opts.Logf("dropping request %d", v.requests)
		return nil
	}
	if domain == universal.Domain_DOMAIN_INFOTAINMENT && v.asleep {
		v.opts.Logf("infotainment asleep; no reply")
		return nil
	}
	switch payload := req.Payload.(type) {
	case *universal.RoutableMessage_SessionInfoRequest:
		return []*universal.RoutableMessage{v.sessionInfoReply(req, domain, payload.SessionInfoRequest.GetPublicKey())}
	case *universal.RoutableMessage_ProtobufMessageAsBytes:
		return v.commandReplies(req, domain)
	}
	return nil
}

func newReply(req *universal.RoutableMessage, domain universal.Domain, echoUUID bool) *universal.RoutableMessage {
	reply := &universal.RoutableMessage{
		ToDestination:   req.GetFromDestination(),
		FromDestination: &universal.Destination{SubDestination: &universal.Destination_Domain{Domain: domain}},
		Uuid:            randomBytes(16),
	}
	// VCSEC usually leaves request_uuid empty, so clients must match its replies by address.
	if echoUUID {
		reply.RequestUuid = req.GetUuid()
	}
	return reply
}

func (v *Vehicle) sessionInfoReply(req *universal.RoutableMessage, domain universal.Domain, public []byte) *universal.RoutableMessage {
	v.record("sessioninfo", public)
	reply := newReply(req, domain, true)
	if v.findKey(public) < 0 {
		info, _ := proto.Marshal(&signatures.SessionInfo{Status: signatures.Session_Info_Status_SESSION_INFO_STATUS_KEY_NOT_ON_WHITELIST})
		reply.Payload = &universal.RoutableMessage_SessionInfo{SessionInfo: info}
		return reply
	}
	verifier, err := v.verifier(domain, public)
	if err != nil {
		return faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_BAD_PARAMETER)
	}
	encoded, tag, err := verifier.SignedSessionInfo(req.GetUuid())
	if err != nil {
		return faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_INTERNAL)
	}
	reply.Payload = &universal.RoutableMessage_SessionInfo{SessionInfo: encoded}
	reply.SubSigData = &universal.RoutableMessage_SignatureData{
		SignatureData: &signatures.SignatureData{
			SigType: &signatures.SignatureData_SessionInfoTag{SessionInfoTag: &signatures.HMAC_Signature_Data{Tag: tag}},
		},
	}
	return reply
}

func faultReply(req *universal.RoutableMessage, domain universal.Domain, fault universal.MessageFault_E) *universal.RoutableMessage {
	reply := newReply(req, domain, true)
	reply.SignedMessageStatus = &universal.MessageStatus{
		OperationStatus:    universal.OperationStatus_E_OPERATIONSTATUS_ERROR,
		SignedMessageFault: fault,
	}
	return reply
}

func (v *Vehicle) commandReplies(req *universal.RoutableMessage, domain universal.Domain) []*universal.RoutableMessage {
	var verifier *authentication.Verifier
	var signer []byte
	plaintext := req.GetProtobufMessageAsBytes()

	if req.GetSignatureData() != nil {
		signer = req.GetSignatureData().GetSignerIdentity().GetPublicKey()
		if v.findKey(signer) < 0 {
			return []*universal.RoutableMessage{faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID)}
		}
		var err error
		if verifier, err = v.verifier(domain, signer); err != nil {
			return []*universal.RoutableMessage{faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_BAD_PARAMETER)}
		}
		if plaintext, err = verifier.Verify(req); err != nil {
			return []*universal.RoutableMessage{verificationErrorReply(req, domain, err)}
		}
	} else if domain != universal.Domain_DOMAIN_VEHICLE_SECURITY || !isInformationRequest(plaintext) {
		// Only VCSEC information requests may be sent without authentication.
		return []*universal.RoutableMessage{faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_INVALID_SIGNATURE)}
	}

	kind := "carserver"
	if domain == universal.Domain_DOMAIN_VEHICLE_SECURITY {
		kind = "vcsec"
	}
	v.record(kind, plaintext)

	if domain == universal.Domain_DOMAIN_INFOTAINMENT && v.busyLeft > 0 {
		v.busyLeft--
		return []*universal.RoutableMessage{faultReply(req, domain, universal.MessageFault_E_MESSAGEFAULT_ERROR_BUSY)}
	}

	var payloads [][]byte
	if domain == universal.Domain_DOMAIN_VEHICLE_SECURITY {
		payloads = v.handleVCSEC(plaintext, signer)
	} else {
		payloads = [][]byte{v.handleCarServer(plaintext)}
	}

	encrypt := verifier != nil && req.GetFlags()&(1<<universal.Flags_FLAG_ENCRYPT_RESPONSE) != 0
	var replies []*universal.RoutableMessage
	for i, payload := range payloads {
		reply := newReply(req, domain, domain == universal.Domain_DOMAIN_INFOTAINMENT)
		reply.Payload = &universal.RoutableMessage_ProtobufMessageAsBytes{ProtobufMessageAsBytes: payload}
		reply.Flags = req.GetFlags()
		if encrypt {
			if err := verifier.Encrypt(reply, authentication.RequestID(req), uint32(i+1)); err != nil {
				v.opts.Logf("encrypt reply: %s", err)
				continue
			}
		}
		replies = append(replies, reply)
	}
	return replies
}

// verificationErrorReply reports why a command failed authentication. Desync errors carry fresh
// session info, exactly as the vehicle does, so the client can resynchronize and retry.
func verificationErrorReply(req *universal.RoutableMessage, domain universal.Domain, err error) *universal.RoutableMessage {
	code := universal.MessageFault_E_MESSAGEFAULT_ERROR_INTERNAL
	reply := newReply(req, domain, true)
	var sigErr *authentication.InvalidSignatureError
	var authErr *authentication.Error
	if errors.As(err, &sigErr) {
		code = sigErr.Code
		if sigErr.EncodedInfo != nil {
			reply.Payload = &universal.RoutableMessage_SessionInfo{SessionInfo: sigErr.EncodedInfo}
			reply.SubSigData = &universal.RoutableMessage_SignatureData{
				SignatureData: &signatures.SignatureData{
					SigType: &signatures.SignatureData_SessionInfoTag{SessionInfoTag: &signatures.HMAC_Signature_Data{Tag: sigErr.Tag}},
				},
			}
		}
	} else if errors.As(err, &authErr) {
		code = authErr.Code
	}
	reply.SignedMessageStatus = &universal.MessageStatus{
		OperationStatus:    universal.OperationStatus_E_OPERATIONSTATUS_ERROR,
		SignedMessageFault: code,
	}
	return reply
}

func isInformationRequest(plaintext []byte) bool {
	var message vcsec.UnsignedMessage
	if err := proto.Unmarshal(plaintext, &message); err != nil {
		return false
	}
	return message.GetInformationRequest() != nil
}

func marshal(message proto.Message) []byte {
	encoded, err := proto.Marshal(message)
	if err != nil {
		panic(err)
	}
	return encoded
}

func (v *Vehicle) withIntermediate(final *vcsec.FromVCSECMessage) [][]byte {
	out := [][]byte{}
	if v.opts.IntermediateReplies {
		out = append(out, marshal(&vcsec.FromVCSECMessage{SubMessage: &vcsec.FromVCSECMessage_CommandStatus{
			CommandStatus: &vcsec.CommandStatus{
				OperationStatus: vcsec.OperationStatus_E_OPERATIONSTATUS_OK,
				SubMessage: &vcsec.CommandStatus_SignedMessageStatus{
					SignedMessageStatus: &vcsec.SignedMessageStatus{Counter: 1},
				},
			},
		}}))
	}
	return append(out, marshal(final))
}

func (v *Vehicle) handleVCSEC(plaintext []byte, signer []byte) [][]byte {
	var message vcsec.UnsignedMessage
	if err := proto.Unmarshal(plaintext, &message); err != nil {
		return [][]byte{marshal(&vcsec.FromVCSECMessage{})}
	}
	switch m := message.SubMessage.(type) {
	case *vcsec.UnsignedMessage_InformationRequest:
		return [][]byte{marshal(v.information(m.InformationRequest))}
	case *vcsec.UnsignedMessage_RKEAction:
		switch m.RKEAction {
		case vcsec.RKEAction_E_RKE_ACTION_LOCK:
			v.locked = true
		case vcsec.RKEAction_E_RKE_ACTION_UNLOCK:
			v.locked = false
		case vcsec.RKEAction_E_RKE_ACTION_AUTO_SECURE_VEHICLE:
			v.locked = true
		case vcsec.RKEAction_E_RKE_ACTION_WAKE_VEHICLE:
			v.scheduleWake()
		}
		return v.withIntermediate(&vcsec.FromVCSECMessage{})
	case *vcsec.UnsignedMessage_ClosureMoveRequest:
		request := m.ClosureMoveRequest
		if request.GetFrontTrunk() == vcsec.ClosureMoveType_E_CLOSURE_MOVE_TYPE_MOVE {
			v.frunkOpen = true
		}
		switch request.GetRearTrunk() {
		case vcsec.ClosureMoveType_E_CLOSURE_MOVE_TYPE_MOVE:
			v.trunkOpen = !v.trunkOpen
		case vcsec.ClosureMoveType_E_CLOSURE_MOVE_TYPE_CLOSE:
			v.trunkOpen = false
		}
		switch request.GetTonneau() {
		case vcsec.ClosureMoveType_E_CLOSURE_MOVE_TYPE_OPEN:
			v.tonneauOpen = true
		case vcsec.ClosureMoveType_E_CLOSURE_MOVE_TYPE_CLOSE:
			v.tonneauOpen = false
		}
		return v.withIntermediate(&vcsec.FromVCSECMessage{})
	case *vcsec.UnsignedMessage_WhitelistOperation:
		return v.withIntermediate(v.whitelistOperation(m.WhitelistOperation, signer))
	}
	return [][]byte{marshal(&vcsec.FromVCSECMessage{})}
}

func (v *Vehicle) scheduleWake() {
	if !v.asleep {
		return
	}
	time.AfterFunc(v.opts.WakeDelay, func() {
		v.mu.Lock()
		v.asleep = false
		v.mu.Unlock()
		v.opts.Logf("infotainment awake")
	})
}

func closure(open bool) vcsec.ClosureState_E {
	if open {
		return vcsec.ClosureState_E_CLOSURESTATE_OPEN
	}
	return vcsec.ClosureState_E_CLOSURESTATE_CLOSED
}

func (v *Vehicle) information(request *vcsec.InformationRequest) *vcsec.FromVCSECMessage {
	switch request.GetInformationRequestType() {
	case vcsec.InformationRequestType_INFORMATION_REQUEST_TYPE_GET_STATUS:
		lock := vcsec.VehicleLockState_E_VEHICLELOCKSTATE_UNLOCKED
		if v.locked {
			lock = vcsec.VehicleLockState_E_VEHICLELOCKSTATE_LOCKED
		}
		sleep := vcsec.VehicleSleepStatus_E_VEHICLE_SLEEP_STATUS_AWAKE
		if v.asleep {
			sleep = vcsec.VehicleSleepStatus_E_VEHICLE_SLEEP_STATUS_ASLEEP
		}
		return &vcsec.FromVCSECMessage{SubMessage: &vcsec.FromVCSECMessage_VehicleStatus{VehicleStatus: &vcsec.VehicleStatus{
			ClosureStatuses: &vcsec.ClosureStatuses{
				FrontTrunk: closure(v.frunkOpen),
				RearTrunk:  closure(v.trunkOpen),
				ChargePort: closure(v.portOpen),
				Tonneau:    closure(v.tonneauOpen),
			},
			VehicleLockState:   lock,
			VehicleSleepStatus: sleep,
			UserPresence:       vcsec.UserPresence_E_VEHICLE_USER_PRESENCE_NOT_PRESENT,
		}}}
	case vcsec.InformationRequestType_INFORMATION_REQUEST_TYPE_GET_WHITELIST_INFO:
		info := &vcsec.WhitelistInfo{NumberOfEntries: uint32(len(v.keys))}
		for i, key := range v.keys {
			digest := sha1.Sum(key.public)
			info.WhitelistEntries = append(info.WhitelistEntries, &vcsec.KeyIdentifier{PublicKeySHA1: digest[:4]})
			info.SlotMask |= 1 << uint(i)
		}
		return &vcsec.FromVCSECMessage{SubMessage: &vcsec.FromVCSECMessage_WhitelistInfo{WhitelistInfo: info}}
	case vcsec.InformationRequestType_INFORMATION_REQUEST_TYPE_GET_WHITELIST_ENTRY_INFO:
		slot := int(request.GetSlot())
		if slot >= len(v.keys) {
			return &vcsec.FromVCSECMessage{}
		}
		key := v.keys[slot]
		digest := sha1.Sum(key.public)
		return &vcsec.FromVCSECMessage{SubMessage: &vcsec.FromVCSECMessage_WhitelistEntryInfo{WhitelistEntryInfo: &vcsec.WhitelistEntryInfo{
			KeyId:          &vcsec.KeyIdentifier{PublicKeySHA1: digest[:4]},
			PublicKey:      &vcsec.PublicKey{PublicKeyRaw: key.public},
			MetadataForKey: &vcsec.KeyMetadata{KeyFormFactor: key.formFactor},
			Slot:           uint32(slot),
			KeyRole:        key.role,
		}}}
	}
	return &vcsec.FromVCSECMessage{}
}

func whitelistStatus(code vcsec.WhitelistOperationInformation_E) *vcsec.FromVCSECMessage {
	status := vcsec.OperationStatus_E_OPERATIONSTATUS_OK
	if code != vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_NONE {
		status = vcsec.OperationStatus_E_OPERATIONSTATUS_ERROR
	}
	return &vcsec.FromVCSECMessage{SubMessage: &vcsec.FromVCSECMessage_CommandStatus{CommandStatus: &vcsec.CommandStatus{
		OperationStatus: status,
		SubMessage: &vcsec.CommandStatus_WhitelistOperationStatus{WhitelistOperationStatus: &vcsec.WhitelistOperationStatus{
			WhitelistOperationInformation: code,
			OperationStatus:               status,
		}},
	}}}
}

func (v *Vehicle) whitelistOperation(op *vcsec.WhitelistOperation, signer []byte) *vcsec.FromVCSECMessage {
	index := v.findKey(signer)
	if index < 0 || v.keys[index].role != keys.Role_ROLE_OWNER {
		return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_NO_PERMISSION_TO_ADD)
	}
	switch sub := op.SubMessage.(type) {
	case *vcsec.WhitelistOperation_AddKeyToWhitelistAndAddPermissions:
		public := sub.AddKeyToWhitelistAndAddPermissions.GetKey().GetPublicKeyRaw()
		if v.findKey(public) >= 0 {
			return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_ATTEMPTING_TO_ADD_KEY_THAT_IS_ALREADY_ON_THE_WHITELIST)
		}
		v.enrollLocked(public, sub.AddKeyToWhitelistAndAddPermissions.GetKeyRole(), op.GetMetadataForKey().GetKeyFormFactor())
	case *vcsec.WhitelistOperation_RemovePublicKeyFromWhitelist:
		public := sub.RemovePublicKeyFromWhitelist.GetPublicKeyRaw()
		if bytes.Equal(public, signer) {
			return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_NO_PERMISSION_TO_REMOVE_ONESELF)
		}
		target := v.findKey(public)
		if target < 0 {
			return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_PUBLIC_KEY_NOT_ON_WHITELIST)
		}
		v.keys = append(v.keys[:target], v.keys[target+1:]...)
	default:
		return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_UNDOCUMENTED_ERROR)
	}
	return whitelistStatus(vcsec.WhitelistOperationInformation_E_WHITELISTOPERATION_INFORMATION_NONE)
}

// handlePresentKey processes a key-card pairing request (an unrouted ToVCSECMessage).
func (v *Vehicle) handlePresentKey(frame []byte, message *vcsec.ToVCSECMessage) {
	v.record("raw", frame)
	signed := message.GetSignedMessage()
	if signed.GetSignatureType() != vcsec.SignatureType_SIGNATURE_TYPE_PRESENT_KEY {
		return
	}
	var unsigned vcsec.UnsignedMessage
	if err := proto.Unmarshal(signed.GetProtobufMessageAsBytes(), &unsigned); err != nil {
		return
	}
	add := unsigned.GetWhitelistOperation().GetAddKeyToWhitelistAndAddPermissions()
	if add == nil {
		return
	}
	public := add.GetKey().GetPublicKeyRaw()
	role := add.GetKeyRole()
	formFactor := unsigned.GetWhitelistOperation().GetMetadataForKey().GetKeyFormFactor()
	if v.opts.PairingApprovalDelay < 0 {
		v.opts.Logf("pairing request received; never approving")
		return
	}
	v.opts.Logf("pairing request received; approving in %s", v.opts.PairingApprovalDelay)
	time.AfterFunc(v.opts.PairingApprovalDelay, func() {
		v.Enroll(public, role, formFactor)
		v.opts.Logf("key card tapped: key enrolled as %s", role)
	})
}

func randomBytes(n int) []byte {
	out := make([]byte, n)
	if _, err := rand.Read(out); err != nil {
		panic(err)
	}
	return out
}
