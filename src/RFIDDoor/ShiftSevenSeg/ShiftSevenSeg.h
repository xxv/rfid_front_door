#include "Arduino.h"

#ifndef SHIFT_SEVEN_SEG_H
#define SHIFT_SEVEN_SEG_H

class ShiftSevenSeg{
  public:
    ShiftSevenSeg(uint8_t strobe, uint8_t data, uint8_t clock, uint8_t output_enable);
    void showHexDigit(uint8_t digit);
    void showAscii(char c);
    void showString(char* c);
    void showString(char* c, int delay_ms);
    void allOn();
    void setDecimalPoint(bool on);
  
  private:
    void shiftBits(uint8_t bits);
/*     
 *   __A__
 * F|     |B
 *  |__G__|
 * E|     |C
 *  |__D__| o DP
 *
 */
 

/* the below mapping is based on an easy-to-wire arrangement with my particular
 * setup. You can change it by creating your own header file */

#ifndef ShiftSevenSeg_pinout
#define ShiftSevenSeg_pinout

#define  S_A _BV(1)
#define  S_B _BV(0)
#define  S_C _BV(2)
#define  S_D _BV(7)
#define  S_E _BV(6)
#define  S_F _BV(5)
#define  S_G _BV(4)
#define  S_DP _BV(3)

#endif

  const byte segments[37] = {
    S_A | S_B | S_C | S_D | S_E | S_F,   // 0
    S_B | S_C,                           // 1
    S_A | S_B | S_G | S_E | S_D,         // 2
    S_A | S_B | S_C | S_D | S_G,         // 3
    S_B | S_C | S_F | S_G,               // 4
    S_A | S_C | S_D | S_F | S_G,         // 5
    S_A | S_C | S_D | S_E | S_F | S_G,   // 6
    S_A | S_B | S_C,                     // 7
    S_A | S_B | S_C | S_D | S_E | S_F | S_G, // 8
    S_A | S_B | S_C | S_F | S_G,          // 9
    S_A | S_B | S_C | S_E | S_F | S_G,    // A  10
    S_C | S_D | S_E | S_F | S_G,          // B
    S_A | S_D | S_E | S_F,                // C
    S_B | S_C | S_D | S_E | S_G,          // D
    S_A | S_D | S_E | S_F | S_G,          // E
    S_A | S_E | S_F | S_G,                // F
    S_A | S_C | S_D | S_E | S_F | S_G,    // G
    S_B | S_C | S_E | S_F | S_G,          // H
    S_E | S_F,                            // I
    S_B | S_C | S_D | S_E,                // J
    0,                                    // no K
    S_D | S_E | S_F,                      // L
    0,                                    // no M
    S_C | S_E | S_G,                      // n
    S_A | S_B | S_C | S_D | S_E | S_F,    // O
    S_A | S_B | S_E | S_F | S_G,          // P
    S_A | S_B | S_C | S_F | S_G,          // q
    S_E | S_G,                            // r
    S_A | S_C | S_D | S_F | S_G,          // S
    S_A | S_B | S_C,                      // T
    S_B | S_C | S_D | S_E | S_F,          // U
    0,                                    // no V
    0,                                    // no W
    0,                                    // no X
    S_B | S_C | S_F | S_G,                // y
    S_A | S_B | S_G | S_E | S_D,          // Z
    S_DP                                  // . 36
  };

  const uint8_t strobe;
  const uint8_t data;
  const uint8_t clock;
  const uint8_t output_enable;

  uint8_t cur;

};

#endif
