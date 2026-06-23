import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const FloatDockApp());

class FloatDockApp extends StatelessWidget {
  const FloatDockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FloatDock',
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: Colors.teal,
        brightness: Brightness.dark,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.edxzvip.floatdock/overlay');

  bool _overlayGranted = false;
  bool _batteryIgnored = false;
  // null = status freeform gak bisa dipastikan di ROM ini (umum terjadi di
  // ColorOS/Realme, MIUI, dll yang gak pakai key Settings.Global standar AOSP)
  bool? _freeformEnabled = false;
  bool _serviceRunning = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Refresh status setiap balik dari halaman Settings
    if (state == AppLifecycleState.resumed) {
      _refreshStatus();
    }
  }

  Future<void> _refreshStatus() async {
    try {
      final overlay =
          await _channel.invokeMethod<bool>('checkOverlayPermission') ?? false;
      final battery =
          await _channel.invokeMethod<bool>('checkBatteryOptimizationIgnored') ??
              false;
      // ✅ cek freeform — bisa null kalau ROM gak punya key yang dikenali
      final freeform =
          await _channel.invokeMethod<bool>('checkFreeformEnabled');
      final running =
          await _channel.invokeMethod<bool>('isServiceRunning') ?? false;
      if (!mounted) return;
      setState(() {
        _overlayGranted = overlay;
        _batteryIgnored = battery;
        _freeformEnabled = freeform;
        _serviceRunning = running;
      });
    } on PlatformException {
      // biarkan status terakhir kalau gagal cek
    }
  }

  Future<void> _requestOverlay() async {
    await _channel.invokeMethod('requestOverlayPermission');
  }

  Future<void> _requestBattery() async {
    await _channel.invokeMethod('requestIgnoreBatteryOptimization');
  }

  // ✅ BARU: buka Developer Options untuk aktifkan freeform
  Future<void> _requestFreeform() async {
    await _channel.invokeMethod('openDeveloperSettings');
  }

  Future<void> _toggleService() async {
    if (!_overlayGranted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Aktifkan izin "Tampil di atas aplikasi lain" dulu ya'),
        ),
      );
      return;
    }
    if (_serviceRunning) {
      await _channel.invokeMethod('stopFloatService');
    } else {
      await _channel.invokeMethod('startFloatService');
    }
    await Future.delayed(const Duration(milliseconds: 300));
    _refreshStatus();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('FloatDock')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Kalau aktif, muncul garis putih kecil di tepi kanan atas layar. '
            'Geser ke kiri buat munculin daftar aplikasi, tap layar kosong buat nutup, '
            'pilih aplikasi buat dibuka mengambang.',
            style: TextStyle(fontSize: 14, color: Colors.white70),
          ),
          const SizedBox(height: 24),
          _PermissionCard(
            title: 'Tampil di atas aplikasi lain',
            subtitle: 'Wajib, buat bikin garis & panel mengambang',
            granted: _overlayGranted,
            onTap: _requestOverlay,
          ),
          const SizedBox(height: 12),
          _PermissionCard(
            title: 'Izinkan berjalan di latar belakang',
            subtitle: 'Disarankan, biar gak gampang dimatiin Android',
            granted: _batteryIgnored,
            onTap: _requestBattery,
          ),
          const SizedBox(height: 12),
          // Card freeform - arahkan ke Developer Options.
          // _freeformEnabled == null artinya ROM ini (banyak terjadi di
          // ColorOS/Realme) gak pakai key Settings standar AOSP, jadi statusnya
          // gak bisa dipastikan otomatis -> tampilkan instruksi manual.
          _FreeformCard(
            status: _freeformEnabled,
            onTap: _requestFreeform,
          ),
          const SizedBox(height: 32),
          FilledButton.icon(
            onPressed: _toggleService,
            icon: Icon(_serviceRunning ? Icons.stop_circle : Icons.play_circle),
            label: Text(
              _serviceRunning
                  ? 'Matikan Floating Switcher'
                  : 'Aktifkan Floating Switcher',
            ),
          ),
        ],
      ),
    );
  }
}

class _FreeformCard extends StatelessWidget {
  final bool? status; // true=aktif, false=nonaktif, null=gak diketahui
  final VoidCallback onTap;

  const _FreeformCard({required this.status, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final Widget trailing;
    if (status == true) {
      trailing = const Chip(label: Text('Aktif'), backgroundColor: Colors.green);
    } else if (status == false) {
      trailing = FilledButton(onPressed: onTap, child: const Text('Aktifkan'));
    } else {
      trailing = OutlinedButton(onPressed: onTap, child: const Text('Cek manual'));
    }

    final subtitle = status == null
        ? 'HP ini (Realme/ColorOS & beberapa merk lain) gak bisa dicek otomatis. '
            'Buka Opsi Developer → cari "Force activities to be resizable" atau '
            '"Aplikasi mengambang", aktifkan manual, lalu restart HP.'
        : 'Buka Opsi Developer → aktifkan "Enable freeform windows" / '
            '"Force activities to be resizable" supaya app bisa mengambang bebas '
            '(bukan split screen / fullscreen)';

    return Card(
      child: ListTile(
        title: const Text('Mode Layar Mengambang'),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: trailing,
      ),
    );
  }
}

class _PermissionCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool granted;
  final VoidCallback onTap;

  const _PermissionCard({
    required this.title,
    required this.subtitle,
    required this.granted,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: granted
            ? const Chip(label: Text('Aktif'), backgroundColor: Colors.green)
            : FilledButton(onPressed: onTap, child: const Text('Izinkan')),
      ),
    );
  }
}
