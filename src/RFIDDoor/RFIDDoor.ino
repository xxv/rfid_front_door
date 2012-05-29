#include <EEPROM.h>
#include <AltSoftSerial.h> 
#include <ShiftSevenSeg.h>

// each ID is truncated to exactly ID_SIZE bytes.
#define ID_SIZE 8

// the IDs are stored in records along with their groups
// this should be ID_SIZE + 1
#define REC_SIZE 9

// the index into the record that stores the groups
#define GRPS_IDX 8

// a group is an integer between 1 and GRP_MAX
#define GRP_MAX 7

// max number of records
// 50 for mega168, 100 for mega328
#define REC_MAX 100

// offset for storing non-record data
#define REC_OFFSET 32

// this group will be set if the power's cycled
#define DEFAULT_GRP 1

// record status flags
// any empty memory should be set to 0
#define REC_END_OF_RECS 0

// a record is marked for reuse. This makes the last bit
// unusable for groups.
#define REC_REUSE 0xff

// return value when a record can't be found
#define NOT_FOUND -1

// buffer size for the command. Commands are truncated to this.
#define CMD_BUFF_SIZE 64

// the Arduino pin with the relay
#define PIN_RELAY 7

// the Arduino pin connected to the button
#define PIN_BUTTON 8
#define DEBOUNCE_THRESHOLD 30

#define PIN_STATUS_LED 13

// PINs for communicating with the shift register
#define PIN_SEG_STROBE 13
#define PIN_SEG_DATA 12
#define PIN_SEG_CLOCK 11
#define PIN_SEG_OUTPUT_ENABLE 10

char* VERSION = "0.2";

uint8_t curGroup = DEFAULT_GRP;

boolean addNext = false;

// RFID reader interface
uint8_t STATION_ID = 0x0;

byte datlen = 0;
byte resStatus = 0;

#define RFID_READ_FREQUENCY 1000
#define RFID_READ_BUFF 64

byte rfidReadBuff[RFID_READ_BUFF];
uint8_t rfidReadIdx = 0;
uint8_t rfidBytesLeft = 0;
boolean isInPacket = 0;

byte idRead[ID_SIZE];
byte sendData[ID_SIZE];

uint8_t newId = 0;

AltSoftSerial rfidReader; // 9,8

ShiftSevenSeg seg = ShiftSevenSeg(PIN_SEG_STROBE, PIN_SEG_DATA, PIN_SEG_CLOCK, 1 /*A*/, 0 /*B*/, 2/*C*/, 7/*D*/, 6/*E*/, 5/*F*/, 4/*G*/, 3/*DP*/);

/**
 * searches all the records for the given
 * ID. Will return NOT_FOUND if no entry
 * can be found.
 */
int findId(byte * id, byte * buf){
  boolean found = false;
  int i;
  
  // search all the IDs for the desired ID
  for (i = 0; i < REC_MAX; i++){
    getRFID(i, buf);
    
    if (REC_REUSE == buf[GRPS_IDX]){
      continue;
      
    }else if(REC_END_OF_RECS == buf[GRPS_IDX]){
      break;
    }
    
    boolean match = true;
  
    // compare the ID bytes  
    for (int j = 0; j < ID_SIZE; j++){
      if (id[j] != buf[j]){
        match = false;
        break; // id_bytes
      }
    }
    
    if (match){
      found = true;
      break; // ids
    }
  }
  return found ? i : NOT_FOUND;
}

/**
 * searches all the records for an empty index
 * to store a new entry in
 */
int findEmptyIndex(){
  byte groups;
  int found = NOT_FOUND;
  
  for (int i = 0; i < REC_MAX; i++){
    groups = getGroupsAtIndex(i);
    if (groups == REC_END_OF_RECS || groups == REC_REUSE){
      found = i;
      break;
    }
  }
  
  return found;
}

/**
 * id is a pointer to the desired ID of size ID_SIZE
 */
byte getGroups(byte * id){
  byte rec[REC_SIZE];
  
  int res = findId(id, rec);
  
  return res != NOT_FOUND ? rec[GRPS_IDX] : 0;
}

/**
 * Sets the groups for a give ID. 
 * id is a pointer to the ID of size ID_SIZE.
 * If there are no entries for the given ID, adds a new one.
 * 
 * Returns true of groups setting was successful. False indicates
 * that storage is full or the record wasn't found and groups
 * was REC_REUSE.
 */
boolean setGroups(byte * id, byte groups){
  byte rec[REC_SIZE];
  
  int pos = findId(id, rec);
  
  if (pos == NOT_FOUND){
    if (groups == REC_REUSE){
      return false;
    }

    // a new record
    pos = findEmptyIndex();
    
    // out of storage space!
    if (pos == NOT_FOUND){
      Serial.println("0\tout of storage space");
      return false;
    }
    
    // copy the ID to the record
    for (int i = 0; i < ID_SIZE; i++){
      rec[i] = id[i];
    }
    rec[GRPS_IDX] = groups;
    
    setRecord(pos, rec);
    
  }else{
    Serial.println("1\tsetting groups for existing record");
    setGroupsAtIndex(pos, groups);
  }
  
  return true;
}

/**
 * returns true if the id is in the given group
 */
boolean checkGroup(byte *id, uint8_t group){
  if (group > GRP_MAX || group == 0){
    return false;
  }
  return getGroups(id) & (1 << (group - 1));
}

/**
 * gets the groups at the given index
 */
byte getGroupsAtIndex(int index){
  int offset = REC_SIZE * index + REC_OFFSET;
  return EEPROM.read(offset + GRPS_IDX);  
}

/**
 * sets the groups for the given index
 */
void setGroupsAtIndex(int index, byte groups){
  int offset = REC_SIZE * index + REC_OFFSET;
  EEPROM.write(offset + GRPS_IDX, groups);
}

/**
 * stores the record at the given index
 */
void setRecord (int index, byte * rec){
  int offset = REC_SIZE * index + REC_OFFSET;
  for (int i = 0; i < REC_SIZE; i++){
    EEPROM.write(i + offset, rec[i]);
  }
}

/**
 * retrieves an RFID from EEPROM at the given index
 * buf must be a size of size REC_SIZE
 */
void getRFID (int index, byte * rec){
  int offset = REC_SIZE * index + REC_OFFSET;
  for (int i = 0; i < REC_SIZE; i++){
    byte b = EEPROM.read(i + offset);
    rec[i] = b;
  }
}

/**
 * list all records
 */
void listRecords(){
  byte rec[REC_SIZE];
  
  int i;
  int cnt = 0;
  boolean hasMore = true;
  for (i = 0; i < REC_MAX && hasMore; i++){
    getRFID(i, rec);
    switch (rec[GRPS_IDX]){
      case REC_END_OF_RECS:
        hasMore = false;
        break;

      case REC_REUSE:
        continue;

      default:
      cnt++;
      listRecord(rec);
    }
  }
  Serial.print("there are ");
  Serial.print(cnt);
  Serial.println(" records.");
}

/**
 * list the given record
 */
void listRecord(byte * rec){
  Serial.print(rec[GRPS_IDX]);
  Serial.print('\t');
  boolean join = false;
  for (int i = 0; i < ID_SIZE; i++){
    if (join){
      Serial.print(':');
    }else{
      join = true;
    }
    
    if (rec[i] < 16){
      Serial.print('0');
    }
    Serial.print(rec[i], HEX);
  }
  Serial.println();
  
}

/**
 * reads a human-readable idStr and stores it in id
 * 
 * returns true if an ID was successfully read.
 */
boolean readId(char * idStr, byte * id){
  int idIdx = 0;
  int len = strlen(idStr);

  // first count digits
  int digits = 0;
  for (int i = 0; i < len; i++){
    switch(idStr[i]){
      case ' ':
      case ':':
        continue;

      default:
        digits++;
    }
  }

  if (digits == 0){
    return false;
  }

  int zeroPad = ID_SIZE - digits / 2;

  // if the string is too long
  if (zeroPad < 0){
    return false;
  }

  // if there are not an even number of digits
  if (digits % 2 != 0){
    return false;
  }

  for (int i = 0; i < zeroPad; i++){
      id[i] = 0;
  }

  for (int i = 0; i < len && idIdx < ID_SIZE; i++){
    switch(idStr[i]){
      case ' ':
      case ':':
        continue;
    }

    sscanf(idStr + i, "%2x", &(id[idIdx + zeroPad]));
    i++; // skip over one character
    idIdx++;
  }
  return true;
}

/**
 * sets the currently active group
 * 
 * group is a bitmask of groups
 */
boolean setCurGroup(uint8_t group){
  if (group <= GRP_MAX && group > 0){
    curGroup = group;
    showCurGroup();
    return true;
  }
  return false;
}

/**
 * gets the currently active group
 */
uint8_t getCurGroup(){
  if (curGroup > GRP_MAX){
    return 0;
  }
  return curGroup;
}

void showCurGroup(){
  seg.showHexDigit(curGroup);
}

/**
 * called when an RFID is scanned
 */
void idScanned(uint8_t status, byte* id){
  if (status != 0){
    return;
  }
  if (addNext){
    if (addIdToCurrentGroup(id)){
      indicateSuccess();
    }else{
      indicateProblem();
    }
    addNext = false;
  }else{
    if (checkGroup(id, getCurGroup())){
      activateRelay();
    }else{
      indicateProblem();
    }
  }
}

/**
 * send a command to the RFID reader
 */
void sendRFIDCmd(byte idCmd, byte datLen, byte* data){
  byte bcc = 0;
  rfidReader.write(0xAA);
  rfidReader.write(STATION_ID);
  rfidReader.write(datLen + 1);
  rfidReader.write(idCmd);
  bcc = STATION_ID ^ (datLen + 1) ^ idCmd;
  for (byte i = 0; i < datLen; i++){
    bcc ^= data[i];
    rfidReader.write(data[i]);
  }
  rfidReader.write(bcc);
  rfidReader.write(0xBB);
}

/**
 * read the results from the RFID request command
 */
void readRfidResult(){
    uint8_t datlen = rfidReadBuff[2] - 1;

    if (datlen > ID_SIZE){
      datlen = ID_SIZE;
    }

    resStatus = rfidReadBuff[3];
    newId = 0;
    uint8_t offset = ID_SIZE - datlen;
    for (uint8_t i = 0; i < datlen; i++){
      byte b = rfidReadBuff[4 + i];
      if (idRead[i + offset] != b){
        newId = 1;
      }
      idRead[i + offset] = b;
    }

    // ensure the rest of the ID data is cleared
    for (uint8_t i = 0; i < ID_SIZE - datlen; i++){
      idRead[i] = 0;
    }

    if (newId){
      idScanned(resStatus, idRead);
    }
}

uint8_t pktDataSize = 0;
uint8_t readReset = 0;

void rfidRead(byte b){
  if (rfidReadIdx < RFID_READ_BUFF){
    if (rfidReadIdx == 0 && b == 0xAA){
      rfidBytesLeft = 4;
      isInPacket = 1;
    }
    if (isInPacket){
      rfidReadBuff[rfidReadIdx] = b;
      if (rfidReadIdx == 2){ // dataLength
        rfidBytesLeft += b;
      }
      rfidReadIdx++;
      rfidBytesLeft--;

      if (rfidBytesLeft == 0){
        readRfidResult();
        isInPacket = 0;
        rfidReadIdx = 0;
      }
    }
  } else if (rfidReadIdx == RFID_READ_BUFF){
    readReset++;
    if (readReset > 10){
      rfidReadIdx = 0;
      readReset = 0;
    }
  }
}

void requestId(){
  sendData[0] = 0x26;
  sendData[1] = 0;
  sendRFIDCmd(0x25, 2, sendData);
}

void indicateSuccess(){

  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(500);
  digitalWrite(PIN_STATUS_LED, LOW);
  delay(100);
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(500);
  digitalWrite(PIN_STATUS_LED, LOW);
  delay(100);
}

void indicateProblem(){
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(100);
  digitalWrite(PIN_STATUS_LED, LOW);
  delay(100);
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(100);
  digitalWrite(PIN_STATUS_LED, LOW);
  delay(100);
  digitalWrite(PIN_STATUS_LED, HIGH);
  delay(100);
  digitalWrite(PIN_STATUS_LED, LOW);
  delay(100);
}

/**
 * activates the relay to open the door
 */
void activateRelay(){
  digitalWrite(PIN_RELAY, HIGH);
  digitalWrite(PIN_STATUS_LED, HIGH);

  // turn the relay on for 1s
  delay(1000);

  digitalWrite(PIN_RELAY, LOW);
  digitalWrite(PIN_STATUS_LED, LOW);
}

void buttonPress(){
  // done in an unusual way, as the groups are stored 1-7 and mod will get us 0-6
  setCurGroup(getCurGroup() % 7 + 1);
  
}

boolean addIdToCurrentGroup(byte * id){
  uint8_t groups = getGroups(id);
  return setGroups(id, groups | (1 << (getCurGroup() - 1)));
}

/**
 * adds the ID to the current group
 */
void addCmd(char * args){
  byte id[ID_SIZE];

  boolean success = false;

  if (readId(args, id)){
    success = addIdToCurrentGroup(id);
  }
  if (success){
    Serial.println("ID added to current group");
  }else{
    Serial.println("error setting groups for ID");
  }
}

boolean removeIdFromCurrentGroup(byte * id){
  uint8_t groups = getGroups(id);
  return setGroups(id, groups & ~(1 << (getCurGroup() - 1)));
}

/**
 * removes the ID from the current group
 */
void removeCmd(char * args){
  byte id[ID_SIZE];
  boolean success = false;

  if (readId(args, id)){
    success = removeIdFromCurrentGroup(id);
  }
  if (success){
    Serial.println("ID removed from current group");
  }else{
    Serial.println("error setting groups for ID");
  }
}


/**
 * deletes the ID from all groups
 */
void delCmd(char * args){
  byte id[ID_SIZE];

  if (readId(args, id) && setGroups(id, REC_REUSE)){
    Serial.println("record deleted");
  }else{
    Serial.println("error deleting record");
  }
}

/**
 * handle the set group command
 */
void setGrpCmd(char *args){
  uint8_t grp;
  if (strlen(args) > 0){
    sscanf(args, "%u", &grp);
    setCurGroup(grp);
  }
  Serial.println(getCurGroup());
}

/**
 * clear all records
 */
void clear(){
  for (int i = 0; i < REC_MAX; i++){
    setGroupsAtIndex(i, REC_END_OF_RECS);
  }
}

char cmd[CMD_BUFF_SIZE];
uint8_t cmdIdx = 0;

void cmdRead(char c){
  switch (c){
    case '\n':
    case '\r':
    // make sure the command is null-terminated
    cmd[cmdIdx] = 0;
    runCmd(cmd);
    cmdIdx = 0;
    break;
    default:
    if (cmdIdx < (CMD_BUFF_SIZE - 1)){
      cmd[cmdIdx] = c;
      cmdIdx++;
    }
  }
}

/**
 * executes the given command.
 * cmd must be null terminated.
 */
void runCmd(char * cmd){
  switch (cmd[0]){
    case 'l':
    listRecords();
    break;
    
    case 'c':
    clear();
    Serial.println("all records cleared");
    break;
    
    case 'a':
    addCmd(cmd + 1);
    break;

    case 'A':
    addNext = true;
    Serial.println("The next card scanned will be added to the current group");
    break;
    
    case 'd':
    delCmd(cmd + 1); 
    break;

    case 'r':
    removeCmd(cmd + 1);
    break;

    case 'o':
      activateRelay();
      break;

    case 'v':
    Serial.print("Version ");
    Serial.println(VERSION);
    break;

    case 'g':
      setGrpCmd(cmd + 1);
      break;

    case 0:
    // don't need to do anything
    break;

    default:
    Serial.println("unrecognized command");
  }
  Serial.println();
}

void setup(){
  Serial.begin(115200);
  rfidReader.begin(9600);
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_STATUS_LED, OUTPUT);
  pinMode(PIN_SEG_OUTPUT_ENABLE, OUTPUT);
  digitalWrite(PIN_SEG_OUTPUT_ENABLE, HIGH);
  showCurGroup();
}

int sendDelay = 0;

uint8_t button_press = 0;

void loop(){
  if (Serial.available()){
    cmdRead(Serial.read());
  }
  if (!isInPacket && sendDelay == 0){
    requestId();
  }
  sendDelay = (sendDelay + 1) % RFID_READ_FREQUENCY;
  if (rfidReader.available()){
    rfidRead(rfidReader.read());
  }

  if (digitalRead(PIN_BUTTON) == LOW){
    if (button_press != 255){
      button_press = (button_press + 1) % 254;
      if (button_press == DEBOUNCE_THRESHOLD){
        buttonPress();
        button_press = 255;
      }
    }
  }else{
    button_press = 0;
  }
}
