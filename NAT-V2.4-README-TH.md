# NAT V2.4 experimental
ใช้ V2.3 เป็นฐาน เพิ่ม NAT est. ในกรอบเดิมเมื่อระยะทางและ Google SOC ตรงกับ forecast อายุไม่เกิน 30 วินาที ไม่มีจุดชาร์จ และ BYD active. ไม่ใช่การยืนยัน route ID; เส้นทางต่างกันอาจมีค่าเท่ากันได้. ค่า Google ยังเป็นตัวเลขหลัก. หากไม่ตรงเงื่อนไขไม่แสดง NAT. ค่าเริ่มต้น 17 kWh/100 km เป็นสมมติฐาน.
ยังเป็น inline ReplacementSpan ไม่ใช่ navigation panel แยก และยังไม่รองรับทุกหน้าจอ. ไม่ได้แก้ Send to Car หรือ adaptive learning. ยังไม่ compile Android.
## Build
แตก ZIP, สร้าง branch nat-v2.4-experimental ใน GitHub Desktop, คัดลอกเนื้อหา ZIP รวม .github เข้า repo, commit/push, Actions > NAT V2.4 integrated patch build > Run workflow เลือก branch นี้. ดาวน์โหลด NAT-V2.4-integrated-mpp. ใน Morphe ใช้ APK ต้นฉบับ 26.10.120003.E และ local MPP ใหม่ เปิด NAT arrival battery V2.4 experimental. เก็บ V2.1 ไว้.
## ทดสอบขณะจอด
Send to Car 10 นาที, ค้นหาหัวหิน, หน้านำทาง, เปลี่ยนเส้นทาง, ตรวจค่าที่ Settings > BYD Battery > NAT Smart EV Prediction, ตรวจว่า NAT หายเมื่อ forecast ไม่สดหรือมีจุดชาร์จ. ไม่ใช้ค่าประมาณเพื่อรับประกันระยะทาง.
