#include <EEPROM.h>
#include <SoftwareSerial.h> 

#define REC_SIZE 9
#define ID_SIZE 8
#define GRP_IDX 8

// max number of records
#define REC_MAX 100
// 50 for 168, 100 for 328

// record status flags
#define REC_END_OF_RECS 0
#define REC_REUSE 0xff

#define NOT_FOUND -1
#define CMD_BUFF_SIZE 64

#define PIN_RELAY 7

#define PIN_STATUS_LED 13

char* VERSION = "0.1";

// RFID reader interface
uint8_t STATION_ID = 0x0;

byte datlen = 0;
byte resStatus = 0;

byte idRead[ID_SIZE];
byte sendData[ID_SIZE];

uint8_t newId = 0;

SoftwareSerial rfidReader(2, 3);

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
    
    if (REC_REUSE == buf[GRP_IDX]){
      continue;
      
    }else if(REC_END_OF_RECS == buf[GRP_IDX]){
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
  
  return res != NOT_FOUND ? rec[GRP_IDX] : 0;
}

/**
 * Sets the groups for a give ID. 
 * id is a pointer to the ID of size ID_SIZE.
 * If there are no entries for the given ID, adds a new one.
 * 
 * Returns true of group setting was successful. False indicates that storage is full.
 */
boolean setGroups(byte * id, byte groups){
  byte rec[REC_SIZE];
  
  int pos = findId(id, rec);
  
  if (pos == NOT_FOUND){
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
    rec[GRP_IDX] = groups;
    
    setRecord(pos, rec);
    
  }else{
    Serial.println("1\tsetting group for existing record");
    setGroupsAtIndex(pos, groups);
  }
  
  return true;
}

/**
 * returns true if the id is in the given group
 */
boolean checkGroup(byte *id, byte group){
  return getGroups(id) & group;
}

/**
 * gets the groups at the given index
 */
byte getGroupsAtIndex(int index){
  int offset = REC_SIZE * index;
  return EEPROM.read(offset + GRP_IDX);  
}

/**
 * sets the groups for the given index
 */
void setGroupsAtIndex(int index, byte groups){
  int offset = REC_SIZE * index;
  EEPROM.write(offset + GRP_IDX, groups);
}

/**
 * stores the record at the given index
 */
void setRecord (int index, byte * rec){
  int offset = REC_SIZE * index;
  for (int i = 0; i < REC_SIZE; i++){
    EEPROM.write(i + offset, rec[i]);
  }
}

/**
 * retrieves an RFID from EEPROM at the given index
 * buf must be a size of size REC_SIZE
 */
void getRFID (int index, byte * rec){
  int offset = REC_SIZE * index;
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
    switch (rec[GRP_IDX]){
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
  Serial.print(rec[GRP_IDX]);
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

void idScanned(uint8_t status, byte* data){
  if (status != 0){
    return;
  }
  listRecord(data);
  if (checkGroup(data, 1)){
    activateRelay();
  }else{
    indicateProblem();
  }
}

void sendCmd(byte cmd, byte datLen, byte* data){
  byte bcc = 0;
  rfidReader.write(0xAA);
  rfidReader.write(STATION_ID);
  rfidReader.write(datLen + 1);
  rfidReader.write(cmd);
  bcc = STATION_ID ^ (datLen + 1) ^ cmd;
  for (byte i = 0; i < datLen; i++){
    bcc ^= data[i];
    rfidReader.write(data[i]);
  }
  rfidReader.write(bcc);
  rfidReader.write(0xBB);
}

void readRfidResult(){
    byte b = blockRead();
    while (b != 0xAA){
      b = blockRead();
    }
    
    uint8_t station = blockRead(); // station ID

    datlen = blockRead();

    if (datlen > ID_SIZE){
      datlen = ID_SIZE;
    }

    resStatus = blockRead();
    newId = 0;
    for (uint8_t i = 0; i < datlen - 1; i++){
      b = blockRead();
      if (idRead[i] != b){
        newId = 1;
      }
      idRead[i] = b;
    }

    // ensure the rest of the ID data is cleared
    for (uint8_t i = datlen - 1; i < ID_SIZE; i++){
      idRead[i] = 0;
    }

    byte bcc = blockRead();

    if (0xBB == blockRead() && newId){
      idScanned(resStatus, idRead);
    }
}

uint8_t blockRead(){
  while(!rfidReader.available()){}
  return rfidReader.read();
}

void requestId(){
  sendData[0] = 0x26;
  sendData[1] = 0;
  sendCmd(0x25, 2, sendData);
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

void activateRelay(){

  // latch the relay on
  digitalWrite(PIN_RELAY, HIGH);
  digitalWrite(PIN_STATUS_LED, HIGH);

  delay(1000);

  digitalWrite(PIN_RELAY, LOW);
  digitalWrite(PIN_STATUS_LED, LOW);

}

/**
 * handle the add command
 */
void addCmd(char * args){
  byte id[ID_SIZE];

  if (readId(args, id) && setGroups(id, 1)){
    Serial.println("groups set for ID");
  }else{
    Serial.println("error setting groups for ID");
  }
}

void delCmd(char * args){
  byte id[ID_SIZE];

  if (readId(args, id) && setGroups(id, REC_REUSE)){
    Serial.println("record deleted");
  }else{
    Serial.println("error deleting record");
  }
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
int cmdIdx = 0;

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
    
    case 'd':
    delCmd(cmd + 1); 
    break;

    case 'r':
      activateRelay();
      break;

    case 'v':
    Serial.print("Version ");
    Serial.println(VERSION);
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
}

void loop(){
  if (Serial.available()){
    if (cmdIdx != CMD_BUFF_SIZE - 1){
      char c = Serial.read();
      
      switch (c){
        case '\n':
        case '\r':
        cmd[cmdIdx] = 0;
        runCmd(cmd);
        cmdIdx = 0;
        break;
        
        default:
        cmd[cmdIdx] = c;
        cmdIdx++;
      }
    }
  }else{
    requestId();
    readRfidResult();
  }
}
