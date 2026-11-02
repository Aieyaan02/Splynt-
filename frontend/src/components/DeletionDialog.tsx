import { useEffect, useRef, useState, type FormEvent } from 'react';
import { accountApi, clearAccessToken } from '../lib/api';
import type { Account } from '../types';

export function DeletionDialog({account,onClose,onChanged,onLogout}:{account:Account;onClose:()=>void;onChanged:(account:Account)=>void;onLogout:()=>void}) {
 const dialog=useRef<HTMLDialogElement>(null);
 const [target,setTarget]=useState('account');
 const [password,setPassword]=useState('');
 const [confirmation,setConfirmation]=useState('');
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [deleted,setDeleted]=useState(false);
 const workspace=account.organizations.find(org=>String(org.id)===target && org.role==='OWNER');
 const expected=workspace?.slug ?? account.email;
 useEffect(()=>{dialog.current?.showModal();},[]);
 async function submit(event:FormEvent) {
  event.preventDefault(); if(busy)return; setBusy(true);setError('');
  try {
   if(deleted){onChanged(await accountApi.getCurrent());onClose();return;}
   if(workspace){
    await accountApi.deleteOrganization(workspace.id,password,confirmation);
    setDeleted(true);setPassword('');
    onChanged(await accountApi.getCurrent());onClose();
   }else{
    await accountApi.deleteAccount(password,confirmation);
    clearAccessToken();onLogout();
   }
  }catch(failure){setError(failure instanceof Error?failure.message:'Unable to complete deletion.');}
  finally{setBusy(false);}
 }
 return <dialog ref={dialog} className="modal compact-modal" aria-labelledby="deletion-title" onCancel={event=>{event.preventDefault();if(!busy)onClose();}}>
  <form className="modal-form" onSubmit={event=>void submit(event)}>
   <header className="modal-header"><h2 id="deletion-title">Delete account or workspace</h2></header>
   {deleted?<p>The workspace was deleted. Refresh your account to continue.</p>:<>
    <label>What to delete<select value={target} disabled={busy} onChange={event=>{setTarget(event.target.value);setConfirmation('');setError('');}}>
     <option value="account">My account</option>
     {account.organizations.filter(org=>org.role==='OWNER').map(org=><option key={org.id} value={String(org.id)}>Workspace: {org.name}</option>)}
    </select></label>
    <p>{workspace?'Permanently removes this workspace, its stores, inventory, history and saved POS credentials for all its members. Other workspaces and member accounts remain.':'Permanently removes your account and signs you out. Delete or transfer any workspaces you own first.'}</p>
    <p>This does not delete anything from your POS or uninstall the app there. Backup copies follow the operator’s retention process.</p>
    <label>Current password<input type="password" autoComplete="current-password" required maxLength={200} value={password} disabled={busy} onChange={event=>setPassword(event.target.value)}/></label>
    <label>Type {expected} to confirm<input required value={confirmation} disabled={busy} onChange={event=>setConfirmation(event.target.value)}/></label>
   </>}
   {error&&<p role="alert" className="message error-message">{error}</p>}
   <footer className="modal-actions"><button className="button secondary" type="button" disabled={busy} onClick={onClose}>Cancel</button>
    <button className="button primary" type="submit" disabled={busy||(!deleted&&confirmation!==expected)}>{busy?'Working…':deleted?'Refresh account':'Permanently delete'}</button></footer>
  </form>
 </dialog>;
}
