#include "Arduino.h"

#ifndef SHIFT_SEVEN_SEG_H
#define SHIFT_SEVEN_SEG_H

class ShiftSevenSeg{
  public:
    ShiftSevenSeg(uint8_t strobe, uint8_t data, uint8_t clock, uint8_t sa, uint8_t sb, uint8_t sc, uint8_t sd, uint8_t se, uint8_t sf, uint8_t sg, uint8_t sdp);
    void showHexDigit(uint8_t digit);
  
  private:
  byte segments[16];
  uint8_t strobe;
  uint8_t data;
  uint8_t clock;
};

#endif
