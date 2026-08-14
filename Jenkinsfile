---
- name: Install NaukriAutomator from Storage Account
  hosts: windows

  vars:
    app_version: "0.1.0"
    installer_name: "NaukriAutomator-Setup-{{ app_version }}.exe"
    blob_url: "https://{{ storage_account }}.blob.core.windows.net/{{ storage_container }}/{{ installer_name }}?{{ sas_token }}"
    remote_tmp: "C:\\Windows\\Temp\\{{ installer_name }}"
    install_root: "C:\\Program Files\\NaukriAutomator"
    product_guid: "1b2ba121-4d86-50ed-acec-34b234644301"

  tasks:

    - name: Download versioned installer from Storage Account
      ansible.windows.win_get_url:
        url: "{{ blob_url }}"
        dest: "{{ remote_tmp }}"

    - name: Check currently installed version
      ansible.windows.win_reg_stat:
        path: "HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{{ product_guid }}"
        name: DisplayVersion
      register: current_install
      failed_when: false

    - name: Report current state
      ansible.builtin.debug:
        msg: >-
          {{ 'No existing install found' if not current_install.exists
             else 'Installed version: ' + current_install.value }}

    - name: Install or upgrade to requested version
      ansible.windows.win_package:
        path: "{{ remote_tmp }}"
        product_id: "{{ product_guid }}"
        arguments: "/S"
        state: present
      register: install_result

    - name: Confirm install path exists
      ansible.windows.win_stat:
        path: "{{ install_root }}"
      register: install_dir_check
      failed_when: not install_dir_check.stat.exists

    - name: Report result
      ansible.builtin.debug:
        msg: "NaukriAutomator {{ app_version }} — changed: {{ install_result.changed }}, path: {{ install_root }}"

    - name: Clean up downloaded installer
      ansible.windows.win_file:
        path: "{{ remote_tmp }}"
        state: absent
