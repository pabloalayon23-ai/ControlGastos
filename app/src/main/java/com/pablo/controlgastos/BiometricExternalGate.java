package com.pablo.controlgastos;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

/**
 * Complements the biometric lock already used by HomeActivity.
 * MainActivity is exported so Excel files can be opened directly from Android;
 * when that happens, this gate prevents bypassing the configured app lock.
 */
public final class BiometricExternalGate {
    private BiometricExternalGate(){}

    public static void install(Activity a){
        if(!(a instanceof MainActivity)) return;
        if(!a.getSharedPreferences("security",Context.MODE_PRIVATE).getBoolean("biometric_lock",false)) return;

        Intent i=a.getIntent();
        String action=i==null?null:i.getAction();
        boolean external=Intent.ACTION_VIEW.equals(action)||Intent.ACTION_SEND.equals(action);
        if(!external) return;

        a.getWindow().getDecorView().postDelayed(()->authenticate(a),180);
    }

    private static void authenticate(Activity a){
        if(a.isFinishing()) return;
        if(Build.VERSION.SDK_INT>=28){
            try{
                CancellationSignal signal=new CancellationSignal();
                BiometricPrompt prompt=new BiometricPrompt.Builder(a)
                        .setTitle("Desbloquear ControlGastos")
                        .setSubtitle("Confirmá tu identidad para importar el archivo")
                        .setNegativeButton("Cancelar",a.getMainExecutor(),(d,w)->a.finish())
                        .build();
                prompt.authenticate(signal,a.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){ }
                    @Override public void onAuthenticationError(int code,CharSequence msg){ if(!a.isFinishing()) a.finish(); }
                });
                return;
            }catch(Exception ignored){}
        }
        fallbackCredential(a);
    }

    private static void fallbackCredential(Activity a){
        try{
            KeyguardManager km=(KeyguardManager)a.getSystemService(Context.KEYGUARD_SERVICE);
            if(km!=null&&km.isKeyguardSecure()){
                Intent credential=km.createConfirmDeviceCredentialIntent("Desbloquear ControlGastos","Confirmá tu identidad para continuar");
                if(credential!=null){a.startActivity(credential);return;}
            }
        }catch(Exception ignored){}
        Toast.makeText(a,"No hay un método seguro configurado en el teléfono",Toast.LENGTH_LONG).show();
        a.finish();
    }
}
