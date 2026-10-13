import {useEffect,useState,type FormEvent} from 'react';
import {Brand} from '../src/components/Brand';
import {PlatformAdmin} from './PlatformAdmin';
import {login,getAccessToken,clearAccessToken,platformApi,ApiRequestError} from './api';
import '../src/index.css';
import '../src/brand.css';
export function AdminApp(){
 const [ready,setReady]=useState(false),[checking,setChecking]=useState(!!getAccessToken()),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let active=true;const expired=()=>{setReady(false);setError('Please sign in again.');};window.addEventListener('platform:expired',expired);
 if(getAccessToken()) void platformApi.access().then(r=>{if(active){setReady(r.allowed);if(!r.allowed){clearAccessToken();setError('Platform admin access is required.');}}}).catch(()=>{if(active)setError('Unable to verify access. Please sign in again.');}).finally(()=>{if(active)setChecking(false);});
 return()=>{active=false;window.removeEventListener('platform:expired',expired);};},[]);
 async function submit(event:FormEvent<HTMLFormElement>){event.preventDefault();const form=event.currentTarget;const data=new FormData(form);setBusy(true);setError('');try{await login(String(data.get('email')).trim(),String(data.get('password')));form.reset();setReady(true);}catch(e){setError(e instanceof ApiRequestError&&e.status===403?'This account does not have platform access.':'Unable to sign in. Check your credentials and try again.');}finally{setBusy(false);}}
 if(checking)return <main className="loading-screen"><Brand/><p>Checking private access…</p></main>;
 if(ready)return <PlatformAdmin onLogout={()=>setReady(false)}/>;
 return <main className="loading-screen"><section className="auth-card" style={{width:'100%',maxWidth:440}}><Brand/><h1>Platform Admin</h1><p>Private access for the Splynt platform owner.</p><form className="form-stack" onSubmit={submit}><label>Email<input name="email" type="email" autoComplete="username" required/></label><label>Password<input name="password" type="password" autoComplete="current-password" required/></label>{error&&<p role="alert">{error}</p>}<button className="button primary" disabled={busy}>{busy?'Signing in…':'Sign in'}</button></form></section></main>;
}
