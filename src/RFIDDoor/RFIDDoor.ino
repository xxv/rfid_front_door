#include <EEPROM.h>

#define REC_SIZE 9
#define ID_SIZE 8
#define GRP_IDX 8
#define REC_MAX 50
#define END_OF_RECORDS 0
#define REUSE_SLOT 0xff
#define NOT_FOUND -1
#define CMD_BUFF_SIZE 64

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
    
    if (REUSE_SLOT == buf[GRP_IDX]){
      continue;
      
    }else if(END_OF_RECORDS == buf[GRP_IDX]){
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
    if (groups == END_OF_RECORDS || groups == REUSE_SLOT){
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
      Serial.println("new entry");
    // a new record
    pos = findEmptyIndex();
    
    // out of storage space!
    if (pos == NOT_FOUND){
      Serial.println("out of storage space");
      return false;
    }
    
    // copy the ID to the record
    for (int i = 0; i < ID_SIZE; i++){
      rec[i] = id[i];
    }
    rec[GRP_IDX] = groups;
    
    setRecord(pos, rec);
    
  }else{
    Serial.println("found record:");
    listRecord(rec);
    Serial.println("setting group for existing record");
    setGroupsAtIndex(pos, groups);
  }
  
  return true;
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
  boolean hasMore = true;
  for (i = 0; i < REC_MAX && hasMore; i++){
    getRFID(i, rec);
    switch (rec[GRP_IDX]){
      case END_OF_RECORDS:
        hasMore = false;
        break;

      case REUSE_SLOT:
        continue;

      default:
      listRecord(rec);
    }
  }
  Serial.print("there are ");
  Serial.print(i);
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
    
    if (rec[i] < 10){
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

  if (readId(args, id) && setGroups(id, REUSE_SLOT)){
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
    setGroupsAtIndex(i, END_OF_RECORDS);
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

    default:
    Serial.println("unrecognized command");
  }
}

void setup(){
  Serial.begin(9600);
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
  }
}
