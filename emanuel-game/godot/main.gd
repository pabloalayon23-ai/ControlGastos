extends Node2D

const W := 1280.0
const H := 720.0
const GROUND_Y := 548.0

var atlas_texture: Texture2D
var player := Sprite2D.new()
var enemies: Array[Dictionary] = []
var coins: Array[Dictionary] = []
var barks: Array[Dictionary] = []
var world_x := 0.0
var speed := 265.0
var velocity_y := 0.0
var on_ground := true
var life := 100
var score := 0
var invulnerable := 0.0
var kicking := 0.0
var pony_active := 0.0
var pony_cooldown := 0.0
var bark_cooldown := 0.0
var enemy_timer := 1.7
var coin_timer := 0.7
var run_clock := 0.0
var game_over := false

var ui := CanvasLayer.new()
var score_label := Label.new()
var life_label := Label.new()
var life_bar := ProgressBar.new()
var pony_button := Button.new()
var bark_button := Button.new()

func _ready():
	atlas_texture = _load_atlas()
	_build_player()
	_build_ui()
	queue_redraw()

func _load_atlas() -> Texture2D:
	var encoded := ""
	for i in range(6):
		encoded += FileAccess.get_file_as_string("res://app/src/main/res/raw/atlas%d.txt" % i)
	var raw := Marshalls.base64_to_raw(encoded)
	var img := Image.new()
	var err := img.load_png_from_buffer(raw)
	if err != OK:
		push_error("No se pudo cargar el atlas de Emanuel: %s" % err)
		var fallback := Image.create(512,256,false,Image.FORMAT_RGBA8)
		fallback.fill(Color(0,0,0,0))
		return ImageTexture.create_from_image(fallback)
	return ImageTexture.create_from_image(img)

func _cell(index:int) -> AtlasTexture:
	var t := AtlasTexture.new()
	t.atlas = atlas_texture
	var col := index % 4
	var row := index / 4
	t.region = Rect2(col*128,row*128,128,128)
	return t

func _build_player():
	player.texture = _cell(1)
	player.position = Vector2(235,GROUND_Y-92)
	player.scale = Vector2(1.46,1.46)
	player.z_index = 20
	add_child(player)

func _button(text:String,pos:Vector2,size:Vector2,color:Color) -> Button:
	var b := Button.new()
	b.text = text
	b.position = pos
	b.size = size
	b.add_theme_font_size_override("font_size",22)
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.corner_radius_top_left = 34
	style.corner_radius_top_right = 34
	style.corner_radius_bottom_left = 34
	style.corner_radius_bottom_right = 34
	style.border_width_left = 3
	style.border_width_right = 3
	style.border_width_top = 3
	style.border_width_bottom = 3
	style.border_color = Color(1,1,1,0.9)
	style.shadow_color = Color(0,0,0,0.35)
	style.shadow_size = 8
	b.add_theme_stylebox_override("normal",style)
	b.add_theme_stylebox_override("pressed",style)
	b.add_theme_stylebox_override("hover",style)
	return b

func _build_ui():
	add_child(ui)
	var panel := Panel.new()
	panel.position = Vector2(18,16)
	panel.size = Vector2(445,120)
	var ps := StyleBoxFlat.new()
	ps.bg_color = Color(0.16,0.08,0.03,0.90)
	ps.corner_radius_top_left = 22
	ps.corner_radius_top_right = 22
	ps.corner_radius_bottom_left = 22
	ps.corner_radius_bottom_right = 22
	ps.border_width_left = 4
	ps.border_width_right = 4
	ps.border_width_top = 4
	ps.border_width_bottom = 4
	ps.border_color = Color(0.62,0.34,0.14)
	panel.add_theme_stylebox_override("panel",ps)
	ui.add_child(panel)

	score_label.position = Vector2(42,30)
	score_label.text = "PUNTOS 0"
	score_label.add_theme_font_size_override("font_size",31)
	ui.add_child(score_label)
	life_label.position = Vector2(44,75)
	life_label.text = "VIDA 100 / 100"
	life_label.add_theme_font_size_override("font_size",20)
	ui.add_child(life_label)
	life_bar.position = Vector2(205,79)
	life_bar.size = Vector2(225,24)
	life_bar.max_value = 100
	life_bar.value = 100
	life_bar.show_percentage = false
	ui.add_child(life_bar)

	var jump := _button("SALTAR",Vector2(885,608),Vector2(112,90),Color(0.10,0.16,0.23,0.92))
	jump.pressed.connect(_jump)
	ui.add_child(jump)
	var kick := _button("PATADA",Vector2(1007,608),Vector2(112,90),Color(0.10,0.16,0.23,0.92))
	kick.pressed.connect(_kick)
	ui.add_child(kick)
	bark_button = _button("MEGA\nLADRIDO",Vector2(1129,597),Vector2(126,101),Color(0.05,0.37,0.86,0.95))
	bark_button.pressed.connect(_bark)
	ui.add_child(bark_button)
	pony_button = _button("PONY",Vector2(1129,485),Vector2(126,101),Color(0.96,0.58,0.03,0.95))
	pony_button.pressed.connect(_pony)
	ui.add_child(pony_button)

func _process(delta):
	if game_over:
		queue_redraw()
		return
	delta = min(delta,0.035)
	world_x += speed*delta
	speed = min(420.0,speed + 1.8*delta)
	run_clock += delta
	if invulnerable > 0: invulnerable -= delta
	if kicking > 0: kicking -= delta
	if pony_active > 0:
		pony_active -= delta
		if pony_active <= 0:
			pony_cooldown = 5.0
	if pony_cooldown > 0: pony_cooldown -= delta
	if bark_cooldown > 0: bark_cooldown -= delta

	velocity_y += 1750.0*delta
	player.position.y += velocity_y*delta
	var base_y := GROUND_Y - (116.0 if pony_active > 0 else 92.0)
	if player.position.y >= base_y:
		player.position.y = base_y
		velocity_y = 0
		on_ground = true

	_animate_player()
	_update_entities(delta)
	score_label.text = "PUNTOS %d" % score
	life_label.text = "VIDA %d / 100" % life
	life_bar.value = life
	pony_button.text = ("PONY %.0fs" % ceil(pony_active)) if pony_active > 0 else (("PONY %.0fs" % ceil(pony_cooldown)) if pony_cooldown > 0 else "PONY")
	bark_button.disabled = pony_active <= 0 or bark_cooldown > 0
	queue_redraw()

func _animate_player():
	if pony_active > 0:
		player.texture = _cell(6)
		player.scale = Vector2(1.65,1.65)
		player.rotation = sin(run_clock*9.0)*0.025
	elif kicking > 0:
		player.texture = _cell(4)
		player.scale = Vector2(1.5,1.5)
		player.rotation = -0.06
	elif not on_ground:
		player.texture = _cell(3)
		player.scale = Vector2(1.5,1.5)
		player.rotation = clamp(velocity_y/2500.0,-0.12,0.12)
	else:
		var frame := 1 if int(run_clock*9.0)%2==0 else 2
		player.texture = _cell(frame)
		player.scale = Vector2(1.46,1.46)
		player.rotation = sin(run_clock*18.0)*0.018
	player.modulate.a = 0.38 if invulnerable > 0 and int(Time.get_ticks_msec()/85)%2==0 else 1.0

func _update_entities(delta):
	enemy_timer -= delta
	coin_timer -= delta
	if enemy_timer <= 0:
		var s := Sprite2D.new()
		s.texture = _cell(7)
		s.scale = Vector2(0.78,0.78)
		s.position = Vector2(1360,GROUND_Y-62)
		s.z_index = 18
		add_child(s)
		enemies.append({"x":1360.0,"node":s})
		enemy_timer = randf_range(1.7,2.9)
	if coin_timer <= 0:
		coins.append({"x":1340.0,"y":randf_range(350.0,465.0),"phase":randf()*TAU})
		coin_timer = randf_range(0.75,1.35)

	for e in enemies.duplicate():
		e.x -= speed*delta
		e.node.position.x = e.x
		if e.x < -120:
			e.node.queue_free(); enemies.erase(e); score += 10
			continue
		if abs(e.x-player.position.x) < 82 and abs((GROUND_Y-60)-(player.position.y+60)) < 95:
			if kicking > 0 or pony_active > 0:
				e.node.queue_free(); enemies.erase(e); score += 25
			elif invulnerable <= 0:
				life = max(0,life-10)
				invulnerable = 1.1
				if life <= 0: game_over = true

	for c in coins.duplicate():
		c.x -= speed*delta
		c.phase += delta*4.2
		if c.x < -70:
			coins.erase(c)
			continue
		var cy := c.y + sin(c.phase)*10.0
		if Vector2(c.x,cy).distance_to(Vector2(player.position.x,player.position.y+55)) < 70:
			coins.erase(c); score += 15

	for b in barks.duplicate():
		b.x += 760.0*delta
		b.life -= delta
		for e in enemies.duplicate():
			if abs(e.x-b.x) < 95:
				e.node.queue_free(); enemies.erase(e); score += 35
		if b.life <= 0 or b.x > 1380: barks.erase(b)

func _jump():
	if game_over: return
	if on_ground:
		velocity_y = -980.0 if pony_active > 0 else -790.0
		on_ground = false

func _kick():
	if game_over or pony_active > 0: return
	kicking = 0.34

func _pony():
	if game_over: return
	if pony_active <= 0 and pony_cooldown <= 0:
		pony_active = 10.0

func _bark():
	if game_over: return
	if pony_active > 0 and bark_cooldown <= 0:
		bark_cooldown = 0.9
		barks.append({"x":player.position.x+95.0,"life":0.62})

func _unhandled_input(event):
	if event.is_action_pressed("jump"): _jump()
	if event.is_action_pressed("kick"): _kick()
	if event.is_action_pressed("pony"): _pony()
	if game_over and event is InputEventScreenTouch and event.pressed:
		get_tree().reload_current_scene()

func _draw():
	# Cielo con bandas suaves
	draw_rect(Rect2(0,0,W,H),Color("7fcaf0"))
	for i in range(7):
		var yy := i*70.0
		draw_rect(Rect2(0,yy,W,75),Color(0.48+0.025*i,0.75+0.018*i,0.90+0.01*i,1))
	# Sol y nubes
	draw_circle(Vector2(1090,105),68,Color(1.0,0.91,0.55,0.55))
	for n in range(7):
		var nx := fmod(n*235.0-world_x*0.035,1510.0)-110.0
		var ny := 95.0+(n%3)*52.0
		for j in range(4): draw_circle(Vector2(nx+j*34,ny+sin(j)*8),34-j*3,Color(1,1,1,0.72))
	# Montañas lejanas
	_draw_mountain_layer(Color("7a8990"),310.0,150.0,world_x*0.05)
	_draw_mountain_layer(Color("476b5d"),392.0,112.0,world_x*0.10)
	# Castillo
	var cx := 915.0-fmod(world_x*0.08,1580.0)
	_draw_castle(Vector2(cx,230))
	# Cascadas
	for wx in [555.0,760.0]:
		var x := wx-fmod(world_x*0.13,1420.0)
		draw_rect(Rect2(x,296,44,210),Color(0.78,0.93,1.0,0.92))
		draw_rect(Rect2(x+11,296,10,210),Color(1,1,1,0.75))
	# Bosque medio con muchos árboles
	for i in range(28):
		var tx := fmod(i*74.0-world_x*0.22,1470.0)-95.0
		var th := 78.0+(i%5)*13.0
		_draw_pine(Vector2(tx,GROUND_Y-26),th,Color("215d3c" if i%2==0 else "2f7047"))
	# Bosque cercano: troncos, copas y hojas
	for i in range(10):
		var tx2 := fmod(i*165.0-world_x*0.34,1650.0)-150.0
		_draw_tree(Vector2(tx2,GROUND_Y-25),i)
	# Suelo rico en detalles
	draw_rect(Rect2(0,GROUND_Y-19,W,24),Color("4f8f3d"))
	draw_rect(Rect2(0,GROUND_Y+5,W,H-GROUND_Y),Color("5c442d"))
	for i in range(45):
		var sx := fmod(i*39.0-world_x*0.56,1320.0)-25.0
		var sy := GROUND_Y+28+(i%4)*31
		draw_circle(Vector2(sx,sy),8+(i%3)*3,Color("776047"))
	for i in range(60):
		var gx := fmod(i*27.0-world_x*0.58,1310.0)-15.0
		var gy := GROUND_Y-18
		draw_line(Vector2(gx,gy),Vector2(gx+(-7 if i%2==0 else 7),gy-12-(i%3)*4),Color("8fcf55"),3,true)
	# Monedas de pata
	for c in coins:
		var cy := c.y+sin(c.phase)*10.0
		_draw_paw_coin(Vector2(c.x,cy))
	# Mega ladrido
	for b in barks:
		for j in range(3):
			draw_arc(Vector2(b.x+j*24,player.position.y+55),34+j*11,-0.8,0.8,18,Color(0.2,0.78,1.0,0.9),6,true)
	if game_over:
		draw_rect(Rect2(360,215,560,245),Color(0.04,0.06,0.08,0.88))
		draw_string(ThemeDB.fallback_font,Vector2(515,315),"SIN VIDA",HORIZONTAL_ALIGNMENT_LEFT,300,54,Color.WHITE)
		draw_string(ThemeDB.fallback_font,Vector2(445,385),"Tocá para volver a jugar",HORIZONTAL_ALIGNMENT_LEFT,410,28,Color.WHITE)

func _draw_mountain_layer(color:Color,base:float,height:float,offset:float):
	var pts := PackedVector2Array()
	pts.append(Vector2(-120,base))
	for i in range(8):
		var x := i*220.0-fmod(offset,220.0)-80.0
		pts.append(Vector2(x,base))
		pts.append(Vector2(x+110,base-height-(i%3)*30))
		pts.append(Vector2(x+220,base))
	pts.append(Vector2(W+120,base))
	pts.append(Vector2(W+120,GROUND_Y))
	pts.append(Vector2(-120,GROUND_Y))
	draw_colored_polygon(pts,color)

func _draw_pine(pos:Vector2,h:float,color:Color):
	draw_rect(Rect2(pos.x-5,pos.y-h*0.42,10,h*0.42),Color("5b432c"))
	for j in range(3):
		var yy := pos.y-h*(0.3+j*0.25)
		var ww := h*(0.34-j*0.055)
		var tri := PackedVector2Array([Vector2(pos.x,yy-h*0.28),Vector2(pos.x-ww,yy+h*0.18),Vector2(pos.x+ww,yy+h*0.18)])
		draw_colored_polygon(tri,color.lightened(j*0.035))

func _draw_tree(pos:Vector2,seed:int):
	var h := 155.0+(seed%4)*18.0
	draw_line(Vector2(pos.x,pos.y),Vector2(pos.x+sin(seed)*9,pos.y-h),Color("65442a"),18,true)
	for k in range(12):
		var a := k*0.75+seed
		var r := 33.0+(k%3)*6
		var center := Vector2(pos.x+cos(a)*48,pos.y-h+sin(a)*32)
		draw_circle(center,r,Color("3c7f42" if k%2==0 else "4c9449"))
		draw_circle(center+Vector2(-6,-6),r*0.6,Color(0.42,0.67,0.28,0.5))

func _draw_castle(pos:Vector2):
	var stone := Color("c7b38c")
	for xoff in [0.0,72.0,148.0]:
		draw_rect(Rect2(pos.x+xoff,pos.y,54,106),stone)
		var roof := PackedVector2Array([Vector2(pos.x+xoff-8,pos.y),Vector2(pos.x+xoff+27,pos.y-45),Vector2(pos.x+xoff+62,pos.y)])
		draw_colored_polygon(roof,Color("a64632"))
	draw_rect(Rect2(pos.x+36,pos.y+46,130,60),stone.darkened(0.08))
	for j in range(7): draw_rect(Rect2(pos.x+12+j*25,pos.y+28+(j%2)*38,7,15),Color("33404a"))

func _draw_paw_coin(pos:Vector2):
	draw_circle(pos,28,Color(1.0,0.62,0.03,0.28))
	draw_circle(pos,23,Color("ffb51f"))
	draw_circle(pos,19,Color("ffd65a"))
	draw_circle(pos+Vector2(0,7),8,Color("d98a00"))
	for p in [Vector2(-9,-6),Vector2(-3,-13),Vector2(5,-13),Vector2(11,-5)]: draw_circle(pos+p,4.7,Color("d98a00"))
